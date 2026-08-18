/*
 * Copyright 2022-2024 Cyface GmbH
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

import com.mongodb.MongoWriteException
import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.UploadAlreadyExists
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.core.file.OpenOptions
import io.vertx.core.streams.ReadStream
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.absolutePathString

/**
 * A storage service to write the data to Mongo database Grid FS.
 *
 * @author Klemens Muthmann
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

    /**
     * Carries out the actual writing of an upload, which is involved enough to warrant its own object.
     */
    private val uploadOperation = GridFsUploadOperation(dao, fs)

    override fun store(
        sourceData: ReadStream<Buffer>,
        uploadMetaData: UploadMetaData
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        val sourceToTempPipe = sourceData.pipe()
        val temporaryStorageFile = pathToTemporaryFile(uploadMetaData.uploadIdentifier)
        val fsOpenCall = fs.open(temporaryStorageFile.absolutePathString(), OpenOptions().setAppend(true))
        fsOpenCall.onSuccess { asyncFile ->
            val storeCall = uploadOperation.store(
                temporaryStorageFile,
                asyncFile,
                sourceToTempPipe,
                uploadMetaData
            )
            storeCall.onSuccess(ret::complete)
            storeCall.onFailure { cause ->
                if (cause is MongoWriteException && cause.code == MONGO_DUPLICATE_ENTRY_ERROR_CODE) {
                    ret.fail(
                        UploadAlreadyExists(
                            "Duplicate entry in database for upload ${uploadMetaData.uploadable}!",
                            cause
                        )
                    )
                } else {
                    LOGGER.error("Internal Server Error during database access!", cause)
                    ret.fail(cause)
                }
            }
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

    override fun isStored(deviceId: String, measurementId: Long, attachmentId: Long): Future<Boolean> {
        return dao.exists(deviceId, measurementId, attachmentId)
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
        return uploadFolder.resolve(uploadIdentifier.toString())
    }

    companion object {
        /**
         * The logger used by objects of this class. Configure it using `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(GridFsStorageService::class.java)
        private const val MONGO_DUPLICATE_ENTRY_ERROR_CODE = 11000
    }
}
