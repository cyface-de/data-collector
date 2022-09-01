package de.cyface.collector.storage

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.storage.exception.GridFsFailed
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.exception.ContentRangeNotMatchingFileSize
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.Pipe
import io.vertx.ext.mongo.GridFsUploadOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.mongo.MongoGridFsClient
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

class GridFsStorageService(val mongoClient: MongoClient, val fs: FileSystem): DataStorageService {

    val uploadFolder: File
        get() {
            val uploadFolder = FILE_UPLOADS_FOLDER.toFile()
            if (!uploadFolder.exists()) {
                Validate.isTrue(uploadFolder.mkdir())
            }
            return uploadFolder
        }

    override fun store(pipe: Pipe<Buffer>, user: User, contentRange: ContentRange, metaData: RequestMetaData, path: String?): Future<Status> {
        val ret = Promise.promise<Status>()

        if(path==null) {
            val acceptUploadResult = acceptUpload(pipe, user, contentRange, metaData)
            acceptUploadResult.onSuccess { result -> ret.complete(result) }
            acceptUploadResult.onFailure { cause -> ret.fail(cause) }
        } else {

            // Search for previous upload chunk
            //request.pause()
            fs.exists(path).onSuccess { fileExists: Boolean? ->
                try {
                    //request.resume()
                    if (!fileExists!!) {
                        // TODO
                        session.remove<Any>(UPLOAD_PATH_FIELD) // was linked to non-existing file
                        val acceptUploadResult = acceptUpload(pipe, user, contentRange, metaData)
                        acceptUploadResult.onSuccess { result -> ret.complete(result) }
                        acceptUploadResult.onFailure { cause -> ret.fail(cause) }
                    } else {
                        // TODO
                        val handleSubsequentChunkUploadResult = handleSubsequentChunkUpload(pipe, user, contentRange, metaData, path)
                        handleSubsequentChunkUploadResult.onSuccess { result -> ret.complete(result) }
                        handleSubsequentChunkUploadResult.onFailure { cause -> ret.fail(cause) }
                    }
                } catch (e: RuntimeException) {
                    ret.fail(e)
                }
            }.onFailure { cause: Throwable? ->
                ret.fail(cause)
            }
        }

        return ret.future()
    }

    override fun isStored(measurement: Measurement): Future<Boolean> {
        TODO("Not yet implemented")
    }

    override fun bytesUploaded(measurement: Measurement): Future<Long> {
        TODO("Not yet implemented")
    }

    // Cache found, expecting upload to continue the cached file
    /**
     *
     * @param ctx The Vertx `RoutingContext` used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     * @param path `String` if the session contained a path to the chunk file or `null` otherwise.
     */
    private fun handleSubsequentChunkUpload(
        //ctx: RoutingContext,
        //request: HttpServerRequest,
        //session: Session,
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        metaData: RequestMetaData,
        path: String,
    ): Future<Status> {
        val promise = Promise.promise<Status>()
        //request.pause()
        //val fs = ctx.vertx().fileSystem()
        fs.props(path).onSuccess { props: FileProps ->
            //request.resume()
            // Wrong chunk uploaded
            val byteSize = props.size()
            if (contentRange.fromIndex != byteSize.toString()) {
                // Ask client to resume from the correct position
                val range = String.format("bytes=0-%d", byteSize - 1)
                LOGGER.debug("Response: 308, Range {} (partial data)", range)
                /*ctx.response().putHeader("Range", range)
                ctx.response().putHeader("Content-Length", "0")
                ctx.response().setStatusCode(StatusHandler.RESUME_INCOMPLETE).end()
                return@onSuccess*/
                promise.complete(Status.INCOMPLETE)
            } else {
                val acceptUploadResult = acceptUpload(pipe, user, contentRange, metaData)
                acceptUploadResult.onSuccess { result -> promise.complete(result) }
                acceptUploadResult.onFailure { cause -> promise.fail(cause) }
            }
        }.onFailure { cause: Throwable? ->
            LOGGER.error("Response: 500, failed to read props from temp file")
            promise.fail(cause)
        }

        return promise.future()
    }

    /**
     * Creates a new upload temp file and calls [.acceptUpload].
     *
     * @param ctx The Vertx `RoutingContext` used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     */
    private fun acceptUpload(
        //ctx: RoutingContext,
        //request: HttpServerRequest,
        //session: Session,
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        metaData: RequestMetaData,
    ): Future<Status> {
        val promise = Promise.promise<Status>()

        // Create temp file to accept binary
        val fileName = UUID.randomUUID().toString()
        val tempFile = Paths.get(uploadFolder.path, fileName).toAbsolutePath().toFile()

        // TODO
        // Bind session to this measurement and mark as "pre-request accepted"
        session.put(UPLOAD_PATH_FIELD, tempFile)
        val acceptUploadResult = acceptUpload(pipe, user, contentRange, tempFile, metaData)
        acceptUploadResult.onSuccess { result -> promise.complete(result) }
        acceptUploadResult.onFailure { cause -> promise.fail(cause) }
        return promise.future()
    }

    /**
     * Streams the request body into a temp file and persists the file after it's fully uploaded.
     *
     * @param ctx The Vertx `RoutingContext` used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     */
    private fun acceptUpload(
        pipe: Pipe<Buffer>,
        //ctx: RoutingContext,
        //request: HttpServerRequest,
        //session: Session,
        user: User,
        contentRange: ContentRange,
        tempFile: File,
        metaData: RequestMetaData,
    ): Future<Status> {
        val promise = Promise.promise<Status>()

        //request.pause()
        fs.open(tempFile.absolutePath, OpenOptions().setAppend(true)).onSuccess { asyncFile: AsyncFile ->
            //request.resume()

            // Pipe body to reduce memory usage and store body of interrupted connections (to support resume)
            pipe.to(asyncFile).onSuccess {
            //request.pipeTo(asyncFile).onSuccess { success: Void? ->
                // Check if the upload is complete or if this was just a chunk
                fs.props(tempFile.toString()).onSuccess { props: FileProps ->

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
                        /*ctx.response().putHeader("Range", range)
                        ctx.response().putHeader("Content-Length", "0")
                        ctx.response().setStatusCode(StatusHandler.RESUME_INCOMPLETE).end()
                        return@onSuccess*/
                        promise.complete(Status.INCOMPLETE)
                    } else {
                        // Persist data
                        val measurement =
                            Measurement(metaData, user.idString, tempFile)
                        val storeToMongoDBResult = storeToMongoDB(measurement, fs)
                        storeToMongoDBResult.onSuccess { result -> promise.complete(Status.COMPLETE) }
                        storeToMongoDBResult.onFailure { cause -> promise.fail(cause) }
                    }
                }.onFailure { cause: Throwable? ->
                    LOGGER.error("Response: 500, failed to read props from temp file")
                    promise.fail(cause)
                }
            }.onFailure { cause: Throwable ->
                /*if (failure.javaClass == PayloadTooLarge::class.java) {
                    remove(session, tempFile) // client won't resume
                    LOGGER.error(String.format("Response: 422: %s", failure.message), failure)
                    ctx.fail(ENTITY_UNPARSABLE, failure.cause)
                    return@onFailure
                }*/
                // Not cleaning session/uploads to allow resume
                LOGGER.error("Response: 500", cause)
                promise.fail(cause)
            }
        }.onFailure { cause: Throwable? ->
            LOGGER.error("Unable to open temporary file to stream request to!", cause)
            promise.fail(cause)
        }

        return promise.future()
    }

    /**
     * Stores a [Measurement] to a Mongo database. This method never fails. If a failure occurs it is logged and
     * status code 422 is used for the response.
     *
     * @param measurement The measured data to write to the Mongo database
     * @param ctx The Vertx `RoutingContext` used to write the response
     */
    fun storeToMongoDB(measurement: Measurement): Future<String> {
        val promise = Promise.promise<String>()
        LOGGER.debug(
            "Inserted measurement with id {}:{}!", measurement.metaData.deviceIdentifier,
            measurement.metaData.measurementIdentifier
        )
        mongoClient.createDefaultGridFsBucketService().onSuccess { gridFs: MongoGridFsClient ->
            val fileUpload = measurement.binary
            val openFuture = fs.open(fileUpload.absolutePath, OpenOptions())
            val uploadFuture = openFuture.compose { file: AsyncFile? ->
                val options = GridFsUploadOptions()
                val metaData = measurement.toJson()
                options.metadata = JsonObject(metaData.toString())
                gridFs.uploadByFileNameWithOptions(file, fileUpload.name, options)
            }

            // Wait for all file uploads to complete
            uploadFuture.onSuccess { result: String ->
                // Not removing session to allow the client to check the upload status if interrupted
                LOGGER.debug("Response: 201")
                promise.complete(result)
                //clean(ctx.session(), measurement.binary)
                //ctx.response().setStatusCode(201).end()
            }.onFailure { cause: Throwable ->
                LOGGER.error("Unable to store file to MongoDatabase!", cause)
                promise.fail(cause)
            }
        }.onFailure { cause: Throwable ->
            LOGGER.error("Unable to open connection to Mongo Database!", cause)
            promise.fail(GridFsFailed(cause))
        }
        return promise.future()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(GridFsStorageService::class.java)
        /**
         * The folder to cache file uploads until they are persisted.
         */
        @JvmField
        val FILE_UPLOADS_FOLDER = Path.of("file-uploads/")
    }
}
