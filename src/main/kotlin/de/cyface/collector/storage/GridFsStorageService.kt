package de.cyface.collector.storage

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.RequestMetaData
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
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name


class GridFsStorageService(private val mongoClient: MongoClient, val fs: FileSystem): DataStorageService {

    private val uploadFolder: File
        get() {
            val uploadFolder = FILE_UPLOADS_FOLDER.toFile()
            if (!uploadFolder.exists()) {
                Validate.isTrue(uploadFolder.mkdir())
            }
            return uploadFolder
        }

    override fun store(pipe: Pipe<Buffer>, user: User, contentRange: ContentRange, uploadIdentifier: UUID, metaData: RequestMetaData): Future<Status> {
        val promise = Promise.promise<Status>()
        val temporaryStorageFile = pathToTemporaryFile(uploadIdentifier)
        val fsOpenResult = fs.open(temporaryStorageFile.absolutePathString(), OpenOptions().setAppend(true))
        fsOpenResult.onSuccess { asyncFile: AsyncFile ->

            // Pipe body to reduce memory usage and store body of interrupted connections (to support resume)
            val pipeResult = pipe.to(asyncFile)
            pipeResult.onSuccess {
                // Check if the upload is complete or if this was just a chunk
                val fsPropsResult = fs.props(temporaryStorageFile.toString())
                fsPropsResult.onSuccess { props: FileProps ->

                    // We could reuse information but to be sure we check the actual file size
                    val byteSize = props.size()
                    if (byteSize - 1 != contentRange.toIndex.toLong()) {
                        LOGGER.error("Response: 500, Content-Range ({}) not matching file size ({} - 1)", contentRange, byteSize)
                        promise.fail(ContentRangeNotMatchingFileSize())
                    } else if (contentRange.toIndex.toLong() != contentRange.totalBytes.toLong() - 1) {
                        // This was not the final chunk of data
                        // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                        val range = String.format("bytes=0-%d", byteSize - 1)
                        LOGGER.debug("Response: 308, Range {}", range)
                        clean(uploadIdentifier)
                            .onSuccess { promise.complete(Status(uploadIdentifier, StatusType.INCOMPLETE, byteSize)) }
                            .onFailure { cause -> promise.fail(cause) }
                    } else {
                        // Persist data
                        val measurement =
                            Measurement(metaData, user.idString, temporaryStorageFile.toFile())
                        val storeToMongoDBResult = storeToMongoDB(measurement, temporaryStorageFile)
                        storeToMongoDBResult.onSuccess {
                            LOGGER.debug("Stored measurement {}:{} under object id {}!", measurement.metaData.deviceIdentifier, measurement.metaData.measurementIdentifier, it.toString())
                            promise.complete(Status(uploadIdentifier, StatusType.COMPLETE, byteSize))
                        }
                        storeToMongoDBResult.onFailure {
                            LOGGER.debug("Failed to store measurement {}:{}!", measurement.metaData.deviceIdentifier, measurement.metaData.measurementIdentifier)
                            promise.fail(it)
                        }
                    }
                }
                fsPropsResult.onFailure { cause: Throwable ->
                    LOGGER.error("Response: 500, failed to read props from temp file")
                    promise.fail(cause)
                }
            }
            pipeResult.onFailure { cause: Throwable ->
                LOGGER.error("Response: 500", cause)
                promise.fail(cause)
            }
        }
        fsOpenResult.onFailure { cause: Throwable ->
            LOGGER.error("Unable to open temporary file to stream request to!", cause)
            promise.fail(cause)
        }

        return promise.future()
    }

    override fun isStored(deviceId: String, measurementId: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()
        val access = mongoClient.createDefaultGridFsBucketService()
        access.onSuccess { gridFs: MongoGridFsClient ->
            try {
                val query = JsonObject()
                query.put("metadata.deviceId", deviceId)
                query.put("metadata.measurementId", measurementId.toString())
                val findIds = gridFs.findIds(query)
                findIds.onFailure(ret::fail)
                findIds.onSuccess { ids: List<String> ->
                    try {
                        if (ids.size > 1) {
                            LOGGER.error("More than one measurement found for did {} mid {}", deviceId, measurementId)
                            ret.fail(
                                DuplicatesInDatabase(
                                    String.format(
                                        "Found %d datasets with deviceId %s and measurementId %d",
                                        ids.size,
                                        deviceId,
                                        measurementId
                                    )
                                )
                            )
                        } else if (ids.size == 1) {
                            ret.complete(true)
                        } else {
                            ret.complete(false)
                        }
                    } catch (exception: RuntimeException) {
                        ret.fail(exception)
                    }
                }
            } catch (exception: RuntimeException) {
                ret.fail(exception)
            }
        }

        return ret.future()
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

    override fun startPeriodicCleaningOfTempData(uploadExpirationTime: Long, vertx: Vertx) {
        // Schedule upload file cleaner task
        vertx.setPeriodic(uploadExpirationTime) {
            // Remove deprecated temp files
            // There is no way to look through all sessions to identify unreferenced files. Thus, we remove files which
            // have not been changed for a long time. The MeasurementHandler handles sessions with "missing" files.
            fs.readDir(FILE_UPLOADS_FOLDER.absolutePathString()).onSuccess { uploadFiles ->
                uploadFiles.filter { pathname ->
                    val path = Paths.get(pathname)
                    path.isRegularFile() && path.getLastModifiedTime().toMillis() > uploadExpirationTime
                }.forEach { pathname ->
                    LOGGER.debug("Cleaning up temp file: {}", pathname)
                    fs.delete(pathname).onFailure {
                        LOGGER.warn("Failed to remove temp file: {}", pathname)
                    }
                }
            }
        }
    }

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
            "Insert measurement with id {}:{}!", measurement.metaData.deviceIdentifier,
            measurement.metaData.measurementIdentifier
        )

        //////
        /*val compositeResult = CompositeFuture.all<MongoGridFsClient, AsyncFile>(
            mongoClient.createDefaultGridFsBucketService(),
            fs.open(temporaryStorage.absolutePathString(), OpenOptions()))
        val uploadResult = compositeResult.compose {
            val gridFs = it.resultAt<MongoGridFsClient>(0)
            val file = it.resultAt<AsyncFile>(1)
            val options = GridFsUploadOptions()
            options.metadata = measurement.toJson()
            gridFs.uploadByFileNameWithOptions(
                file,
                temporaryStorage.name,
                options
            )
        }

        uploadResult.onSuccess {idString -> ObjectId(idString)}
        uploadResult.onFailure(promise::fail)*/
        //////
        val bucketServiceCreationResult = mongoClient.createDefaultGridFsBucketService()
        bucketServiceCreationResult.onFailure(promise::fail)
        bucketServiceCreationResult.onSuccess { gridfs ->
            val temporaryFileOpenResult = fs.open(temporaryStorage.absolutePathString(), OpenOptions())
            temporaryFileOpenResult.onFailure(promise::fail)
            temporaryFileOpenResult.onSuccess { temporaryStorageFile ->
                val options = GridFsUploadOptions()
                options.metadata = measurement.toJson()
                val uploadResult = gridfs.uploadByFileNameWithOptions(
                    temporaryStorageFile,
                    temporaryStorage.name,
                    options
                )
                uploadResult.onFailure(promise::fail)
                uploadResult.onSuccess { objectId -> promise.complete(ObjectId(objectId)) }
            }
        }
        return promise.future()
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