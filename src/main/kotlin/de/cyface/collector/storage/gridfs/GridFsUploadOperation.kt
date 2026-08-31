/*
 * Copyright 2026 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.storage.gridfs

import de.cyface.collector.model.Upload
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.ContentRangeNotMatchingFileSize
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.Pipe
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

/**
 * Writes one upload into Grid FS, by way of the temporary file it was streamed to.
 *
 * An upload does not arrive in one piece. It is streamed to a local temporary file, possibly across several
 * requests, and only the request which completes it may move the data into Grid FS. Carrying that out means
 * draining the request into the file, comparing what arrived against what the client announced, and either
 * reporting the upload as still incomplete or handing it over to storage. Those steps only make sense together
 * and only serve this one purpose, which is why they live here rather than among the lookups and cleanups of
 * [GridFsStorageService].
 *
 * @author Klemens Muthmann
 * @property dao The data access object used to write the completed upload to Grid FS.
 * @property fs The Vert.x file system holding the temporary file an upload is streamed to.
 */
class GridFsUploadOperation(private val dao: GridFsDao, private val fs: FileSystem) {

    /**
     * The logger used by objects of this class. Configure it using `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(GridFsUploadOperation::class.java)

    /**
     * Drain the request into the temporary file and store the result if this completed the upload.
     *
     * @param temporaryStorage The path of the temporary file this upload is streamed to.
     * @param temporaryFile The opened temporary file, ready to append to.
     * @param sourceToTempPipe The request body, as a pipe to drain into [temporaryFile].
     * @param uploadMetaData Describes the upload, in particular which part of it this request carries.
     * @return A [Future] telling whether the upload is now complete or still expects further requests.
     */
    fun store(
        temporaryStorage: Path,
        temporaryFile: AsyncFile,
        sourceToTempPipe: Pipe<Buffer>,
        uploadMetaData: UploadMetaData,
    ): Future<Status> {
        val ret = Promise.promise<Status>()

        // Pipe body to reduce memory usage and store body of interrupted connections (to support resume)
        val pipeToCall = sourceToTempPipe.to(temporaryFile)
        pipeToCall.onSuccess {
            logger.debug("Finished Reading request!")
            // Check if the upload is complete or if this was just a chunk
            val fsPropsCall = fs.props(temporaryStorage.toString())
            fsPropsCall.onSuccess { props -> onFilePropsLoaded(props, ret, uploadMetaData, temporaryStorage) }
            fsPropsCall.onFailure(ret::fail)
        }
        pipeToCall.onFailure(ret::fail)
        return ret.future()
    }

    /**
     * Handles storage of data that is already inside temporary storage on the local disc.
     */
    private fun onFilePropsLoaded(
        props: FileProps,
        promise: Promise<Status>,
        uploadMetaData: UploadMetaData,
        temporaryStorage: Path,
    ) {
        // Checking that the data was actually written successfully.
        logger.debug(
            "Temporary storage contained {} and expected {}.",
            props.size(),
            uploadMetaData.contentRange.totalBytes
        )
        val byteSize = props.size()
        val contentRange = uploadMetaData.contentRange
        val uploadIdentifier = uploadMetaData.uploadIdentifier
        if (byteSize - 1 != contentRange.toIndex) {
            logger.error(
                "Response: 500, Content-Range ({}) not matching file size ({} - 1)",
                contentRange,
                byteSize
            )
            promise.fail(
                ContentRangeNotMatchingFileSize(
                    String.format(
                        Locale.getDefault(),
                        "Response: 500, Content-Range (%s) not matching file size (%d - 1)",
                        contentRange,
                        byteSize
                    )
                )
            )
        } else if (contentRange.toIndex != contentRange.totalBytes - 1) {
            // This was not the final chunk of data
            // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
            logger.debug("Response: 308, Range bytes=0-{}", byteSize - 1)
            promise.complete(Status(uploadIdentifier, StatusType.INCOMPLETE, byteSize))
        } else {
            // Persist data
            val metaData = uploadMetaData.uploadable
            val user = uploadMetaData.user
            val upload = Upload(metaData, user.idString, temporaryStorage.toFile())
            val storeToMongoDBResult = storeToMongoDB(upload, temporaryStorage)
            storeToMongoDBResult.onSuccess {
                logger.debug("Stored upload {} under object id {}!", upload, it.toString())
                promise.complete(Status(uploadIdentifier, StatusType.COMPLETE, byteSize))
            }
            storeToMongoDBResult.onFailure {
                logger.debug("Failed to store upload {}!", upload)
                promise.fail(it)
            }
        }
    }

    /**
     * Stores an [Upload] to a Mongo database.
     *
     * @param upload The measured data to write to the Mongo database.
     * @param temporaryStorage The temporary storage for the uploaded data to store.
     */
    private fun storeToMongoDB(upload: Upload, temporaryStorage: Path): Future<ObjectId> {
        val promise = Promise.promise<ObjectId>()
        logger.debug("Insert upload {}!", upload.uploadable)

        val temporaryFileOpenCall = fs.open(temporaryStorage.absolutePathString(), OpenOptions())
        temporaryFileOpenCall.onFailure(promise::fail)
        temporaryFileOpenCall.onSuccess { temporaryStorageFile ->
            val storeCall = dao.store(upload, temporaryStorage.name, temporaryStorageFile)
            storeCall.onSuccess(promise::complete)
            storeCall.onFailure(promise::fail)
            // GridFS never closes the read handle it is given, so we must close it ourselves once done.
            storeCall.eventually { ->
                temporaryStorageFile.close().recover { cause ->
                    logger.warn("Failed to close temporary file after upload to GridFS.", cause)
                    Future.succeededFuture()
                }
            }
        }
        return promise.future()
    }
}
