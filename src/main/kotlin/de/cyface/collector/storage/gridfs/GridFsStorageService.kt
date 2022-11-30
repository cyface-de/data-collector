/*
 * Copyright 2022 Cyface GmbH
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

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.exception.ContentRangeNotMatchingFileSize
import de.cyface.collector.storage.exception.DuplicatesInDatabase
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.Pipe
import io.vertx.ext.mongo.GridFsUploadOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.mongo.MongoGridFsClient
import org.apache.commons.lang3.Validate
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * A storage service to write the data to Mongo database Grid FS.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property dao A data access object to communicate with GridFs.
 * @property fs The Vert.x file system, used to read the temporarily stored data, from the local disk.
 * @property uploadFolder Local folder to store temporary files,
 * containing the temporary data received, before everything is written to
 * the Grid FS storage.
 */
class GridFsStorageService(
    private val dao: GridFsDao,
    private val fs: FileSystem,
    private val uploadFolder: Path
) : DataStorageService {

    override fun store(
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        uploadIdentifier: UUID,
        metaData: RequestMetaData
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        val temporaryStorageFile = pathToTemporaryFile(uploadIdentifier)
        val fsOpenCall = fs.open(temporaryStorageFile.absolutePathString(), OpenOptions().setAppend(true))
        fsOpenCall.onSuccess { asyncFile ->
            val onTemporaryFileOpenedCall = onTemporaryFileOpened(
                asyncFile,
                contentRange,
                uploadIdentifier,
                pipe,
                temporaryStorageFile,
                metaData,
                user
            )
            onTemporaryFileOpenedCall.onSuccess(ret::complete)
            onTemporaryFileOpenedCall.onFailure(ret::fail)
        }
        fsOpenCall.onFailure { cause: Throwable ->
            LOGGER.error("Unable to open temporary file to stream request to!", cause)
            ret.fail(cause)
        }

        return ret.future()
    }

    override fun isStored(deviceId: String, measurementId: Long): Future<Boolean> {
        return dao.exists(deviceId, measurementId)
    }

    override fun bytesUploaded(uploadIdentifier: UUID): Future<Long> {
        val ret = Promise.promise<Long>()
        val temporaryFileName = pathToTemporaryFile(uploadIdentifier).toFile().absolutePath
        fs.props(temporaryFileName).onSuccess { props: FileProps ->
            // Wrong chunk uploaded
            val byteSize = props.size()
            ret.complete(byteSize)
        }.onFailure(ret::fail)

        return ret.future()
    }

    override fun clean(uploadIdentifier: UUID): Future<Void> {
        val ret = Promise.promise<Void>()
        val pathToTemporaryFile = pathToTemporaryFile(uploadIdentifier)
        fs.delete(pathToTemporaryFile.absolutePathString())
            .onSuccess(ret::complete)
            .onFailure(ret::fail)
        return ret.future()
    }

    override fun startPeriodicCleaningOfTempData(
        uploadExpirationTime: Long,
        vertx: Vertx,
        cleanupOperation: CleanupOperation
    ) {
        // Schedule upload file cleaner task
        vertx.setPeriodic(uploadExpirationTime) {
            cleanupOperation.clean(uploadExpirationTime)
        }
    }

    /**
     * Finds the storage path on the local file system to the temporary data file, based on the `uploadIdentifier`.
     */
    private fun pathToTemporaryFile(uploadIdentifier: UUID): Path {
        return Paths.get(uploadFolder.path, uploadIdentifier.toString())
    }

    /**
     * Stores a [Measurement] to a Mongo database. This method never fails. If a failure occurs it is logged and
     * status code 422 is used for the response.
     *
     * @param measurement The measured data to write to the Mongo database.
     * @param temporaryStorage The temporary storage for the uploaded data to store.
     */
    private fun storeToMongoDB(measurement: Measurement, temporaryStorage: Path): Future<ObjectId> {
        val promise = Promise.promise<ObjectId>()
        LOGGER.debug(
            "Insert measurement with id {}:{}!",
            measurement.metaData.deviceIdentifier,
            measurement.metaData.measurementIdentifier
        )

        val temporaryFileOpenCall = fs.open(temporaryStorage.absolutePathString(), OpenOptions())
        temporaryFileOpenCall.onFailure(promise::fail)
        temporaryFileOpenCall.onSuccess { temporaryStorageFile ->
            val storeCall = dao.store(measurement, temporaryStorage.name, temporaryStorageFile)
            storeCall.onSuccess(promise::complete)
            storeCall.onFailure(promise::fail)
        }
        return promise.future()
    }

    /**
     * Called when opening the local temporary storage has been completed.
     * This function continues with storing the uploaded data provided as a Vertx `Pipe`.
     */
    private fun onTemporaryFileOpened(
        asyncFile: AsyncFile,
        contentRange: ContentRange,
        uploadIdentifier: UUID,
        pipe: Pipe<Buffer>,
        temporaryStorageFile: Path,
        metaData: RequestMetaData,
        user: User
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        // Pipe body to reduce memory usage and store body of interrupted connections (to support resume)
        val pipeToCall = pipe.to(asyncFile)
        pipeToCall.onSuccess {
            // Check if the upload is complete or if this was just a chunk
            val fsPropsCall = fs.props(temporaryStorageFile.toString())
            fsPropsCall.onSuccess { props: FileProps ->

                // Checking that the data was actually written successfully.
                val byteSize = props.size()
                if (byteSize - 1 != contentRange.toIndex) {
                    LOGGER.error(
                        "Response: 500, Content-Range ({}) not matching file size ({} - 1)",
                        contentRange,
                        byteSize
                    )
                    ret.fail(ContentRangeNotMatchingFileSize())
                } else if (contentRange.toIndex != contentRange.totalBytes - 1) {
                    // This was not the final chunk of data
                    // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                    val range = String.format(Locale.ENGLISH, "bytes=0-%d", byteSize - 1)
                    LOGGER.debug("Response: 308, Range {}", range)
                    ret.complete(Status(uploadIdentifier, StatusType.INCOMPLETE, byteSize))
                } else {
                    // Persist data
                    val measurement =
                        Measurement(metaData, user.idString, temporaryStorageFile.toFile())
                    val storeToMongoDBResult = storeToMongoDB(measurement, temporaryStorageFile)
                    storeToMongoDBResult.onSuccess {
                        LOGGER.debug(
                            "Stored measurement {}:{} under object id {}!",
                            measurement.metaData.deviceIdentifier,
                            measurement.metaData.measurementIdentifier,
                            it.toString()
                        )
                        ret.complete(Status(uploadIdentifier, StatusType.COMPLETE, byteSize))
                    }
                    storeToMongoDBResult.onFailure {
                        LOGGER.debug(
                            "Failed to store measurement {}:{}!",
                            measurement.metaData.deviceIdentifier,
                            measurement.metaData.measurementIdentifier
                        )
                        ret.fail(it)
                    }
                }
            }
            fsPropsCall.onFailure { cause: Throwable ->
                LOGGER.error("Response: 500, failed to read props from temp file")
                ret.fail(cause)
            }
        }
        pipeToCall.onFailure { cause: Throwable ->
            LOGGER.error("Response: 500", cause)
            ret.fail(cause)
        }
        return ret.future()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(GridFsStorageService::class.java)

        /**
         * The folder to cache file uploads until they are persisted.
         */
        @JvmField
        val FILE_UPLOADS_FOLDER: Path = Path.of("file-uploads/")
    }
}
