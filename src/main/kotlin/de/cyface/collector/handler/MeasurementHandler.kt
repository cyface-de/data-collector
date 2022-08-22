/*
 * Copyright 2021-2022 Cyface GmbH
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
package de.cyface.collector.handler

import de.cyface.api.Authorizer
import de.cyface.api.PauseAndResumeBeforeBodyParsing
import de.cyface.api.model.User
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.Measurement
import de.cyface.collector.verticle.Config
import de.cyface.model.RequestMetaData
import io.vertx.core.MultiMap
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.mongo.MongoAuthentication
import io.vertx.ext.mongo.GridFsUploadOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.mongo.MongoGridFsClient
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.stream.Collectors

/**
 * A handler for receiving HTTP PUT requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
class MeasurementHandler(
    mongoClient: MongoClient,
    authProvider: MongoAuthentication?,
    payloadLimit: Long
) : Authorizer(authProvider, mongoClient, PauseAndResumeBeforeBodyParsing()) {
    /**
     * Vertx `MongoClient` used to access the database to write the received data to.
     */
    private val mongoClient: MongoClient

    /**
     * The maximum number of `Byte`s which may be uploaded.
     */
    private val payloadLimit: Long

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param config the configuration setting required to start the HTTP server
     */
    constructor(config: Config) : this(config.database, config.authProvider, config.measurementLimit) {}

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param mongoClient The client to use to access the Mongo database.
     * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
     * @param payloadLimit The maximum number of `Byte`s which may be uploaded.
     */
    init {
        Validate.notNull(mongoClient)
        Validate.isTrue(payloadLimit > 0)
        this.mongoClient = mongoClient
        this.payloadLimit = payloadLimit
    }

    override fun handleAuthorizedRequest(
        ctx: RoutingContext,
        users: Set<User>,
        header: MultiMap
    ) {
        LOGGER.info("Received new measurement request.")
        val request = ctx.request()
        val session = ctx.session()
        try {
            // Load authenticated user
            val username = ctx.user().principal().getString("username")
            val matched = users.stream().filter { u: User -> u.name == username }.collect(Collectors.toList())
            Validate.isTrue(matched.size == 1)
            val loggedInUser = matched.stream().findFirst().get() // Make sure it's the matched user
            val bodySize = PreRequestHandler.bodySize(request.headers(), payloadLimit, "content-length")
            val metaData = metaData(request)
            checkSessionValidity(session, metaData)

            // Handle upload status request
            if (bodySize == 0L) {
                StatusHandler(mongoClient).handle(ctx)
                return
            }

            // Handle first chunk
            val contentRange = contentRange(request, bodySize)
            if (session.get<Any?>(UPLOAD_PATH_FIELD) == null) {
                handleFirstChunkUpload(ctx, request, session, loggedInUser, contentRange, metaData, null)
                return
            }

            // Search for previous upload chunk
            val fs = ctx.vertx().fileSystem()
            val path = session.get<Any>(UPLOAD_PATH_FIELD) as String
            request.pause()
            fs.exists(path).onSuccess { fileExists: Boolean? ->
                try {
                    request.resume()
                    if (!fileExists!!) {
                        session.remove<Any>(UPLOAD_PATH_FIELD) // was linked to non-existing file
                        handleFirstChunkUpload(ctx, request, session, loggedInUser, contentRange, metaData, path)
                        return@onSuccess
                    }
                    handleSubsequentChunkUpload(ctx, request, session, loggedInUser, contentRange, metaData, path)
                } catch (e: RuntimeException) {
                    ctx.fail(500, e)
                }
            }.onFailure { failure: Throwable? ->
                LOGGER.error("Response: 500, failed to check if temp file exists")
                ctx.fail(500, failure)
            }
        } catch (e: InvalidMetaData) {
            LOGGER.error(String.format("Response: 422, %s", e.message), e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: Unparsable) {
            LOGGER.error(String.format("Response: 422, %s", e.message), e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: IllegalSession) {
            LOGGER.error(String.format("Response: 422, %s", e.message), e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: PayloadTooLarge) {
            LOGGER.error(String.format("Response: 422, %s", e.message), e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: SessionExpired) {
            LOGGER.warn(String.format("Response: 404, %s", e.message), e)
            ctx.response().setStatusCode(NOT_FOUND).end() // client sends a new pre-request for this upload
        } catch (e: SkipUpload) {
            LOGGER.debug(e.message, e)
            remove(session) // client won't resume
            ctx.fail(PreRequestHandler.PRECONDITION_FAILED, e)
        } catch (e: RuntimeException) {
            ctx.fail(e)
        }
    }

    /**
     * Handles the chunk upload request when there was no chunk file found on the server yet.
     *
     * @param ctx The Vertx `RoutingContext` used to write the response
     * @param request the request to read the body from
     * @param session the session which was passed with the request
     * @param user the user which was authenticated to this request
     * @param contentRange the content range information from the header
     * @param metaData the metadata from the request header
     * @param path `String` if the session contained a path to the chunk file or `null` otherwise.
     */
    private fun handleFirstChunkUpload(
        ctx: RoutingContext,
        request: HttpServerRequest,
        session: Session,
        user: User,
        contentRange: ContentRange,
        metaData: RequestMetaData,
        path: String?
    ) {
        if (contentRange.fromIndex != "0") {
            if (path == null) {
                // I.e. the server received data in a previous request but now cannot find the `path` session value.
                // Unsure when this can happen. Not accepting the data. Asking to restart upload (`404`).
                LOGGER.warn(String.format("Response: 404, path is null and unexpected content range: %s", contentRange))
            } else {
                // Server received data in a previous request but the chunk file was probably cleaned by cleaner task.
                // Can't return 308. Google API client lib throws `Preconditions` on subsequent chunk upload.
                // This makes sense as the server reported before that bytes (>0) were received.
                LOGGER.warn(String.format("Response: 404, Unexpected content range: %s", contentRange))
            }
            ctx.response().setStatusCode(NOT_FOUND).end() // client sends a new pre-request for this upload
            return
        }
        acceptUpload(ctx, request, session, user, contentRange, metaData)
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
        ctx: RoutingContext,
        request: HttpServerRequest,
        session: Session,
        user: User,
        contentRange: ContentRange,
        metaData: RequestMetaData,
        path: String
    ) {
        request.pause()
        val fs = ctx.vertx().fileSystem()
        fs.props(path).onSuccess { props: FileProps ->
            request.resume()
            // Wrong chunk uploaded
            val byteSize = props.size()
            if (contentRange.fromIndex != byteSize.toString()) {
                // Ask client to resume from the correct position
                val range = String.format("bytes=0-%d", byteSize - 1)
                LOGGER.debug(String.format("Response: 308, Range %s (partial data)", range))
                ctx.response().putHeader("Range", range)
                ctx.response().putHeader("Content-Length", "0")
                ctx.response().setStatusCode(StatusHandler.RESUME_INCOMPLETE).end()
                return@onSuccess
            }
            acceptUpload(ctx, request, session, user, contentRange, File(path), metaData)
        }.onFailure { failure: Throwable? ->
            LOGGER.error("Response: 500, failed to read props from temp file")
            ctx.fail(500, failure)
        }
    }

    /**
     * Extracts the metadata from the request header.
     *
     * @param request the request to extract the data from
     * @return the extracted metadata
     * @throws SkipUpload when the server is not interested in the uploaded data
     * @throws InvalidMetaData when the request is missing metadata fields
     */
    @Throws(InvalidMetaData::class, SkipUpload::class)
    private fun metaData(request: HttpServerRequest): RequestMetaData {
        return try {
            // Identifiers
            val deviceId = request.getHeader(FormAttributes.DEVICE_ID.value)
            val measurementId = request.getHeader(FormAttributes.MEASUREMENT_ID.value)
            if (measurementId == null || deviceId == null) {
                throw InvalidMetaData("Measurement- and/or DeviceId missing in header")
            }

            // Location info
            val locationCount = request.getHeader(FormAttributes.LOCATION_COUNT.value).toLong()
            if (locationCount < PreRequestHandler.MINIMUM_LOCATION_COUNT) {
                throw SkipUpload(
                    String.format(
                        "Too few location points %s",
                        locationCount
                    )
                )
            }
            val startLocationLatString = request.getHeader(FormAttributes.START_LOCATION_LAT.value)
            val startLocationLonString = request.getHeader(FormAttributes.START_LOCATION_LON.value)
            val startLocationTsString = request.getHeader(FormAttributes.START_LOCATION_TS.value)
            val endLocationLatString = request.getHeader(FormAttributes.END_LOCATION_LAT.value)
            val endLocationLonString = request.getHeader(FormAttributes.END_LOCATION_LON.value)
            val endLocationTsString = request.getHeader(FormAttributes.END_LOCATION_TS.value)
            if (startLocationLatString == null || startLocationLonString == null || startLocationTsString == null || endLocationLatString == null || endLocationLonString == null) {
                throw InvalidMetaData("Start-/end location data incomplete!")
            }
            val startLocationLat = startLocationLatString.toDouble()
            val startLocationLon = startLocationLonString.toDouble()
            val startLocationTs = startLocationTsString.toLong()
            val endLocationLat = endLocationLatString.toDouble()
            val endLocationLon = endLocationLonString.toDouble()
            val endLocationTs = endLocationTsString.toLong()
            val startLocation = RequestMetaData.GeoLocation(
                startLocationTs, startLocationLat,
                startLocationLon
            )
            val endLocation = RequestMetaData.GeoLocation(endLocationTs, endLocationLat, endLocationLon)

            // Format version
            val formatVersionString = request.getHeader(FormAttributes.FORMAT_VERSION.value)
                ?: throw InvalidMetaData("Data incomplete!")
            val formatVersion = formatVersionString.toInt()

            // Etc.
            val deviceType = request.getHeader(FormAttributes.DEVICE_TYPE.value)
            val osVersion = request.getHeader(FormAttributes.OS_VERSION.value)
            val applicationVersion = request.getHeader(FormAttributes.APPLICATION_VERSION.value)
            val length = request.getHeader(FormAttributes.LENGTH.value).toDouble()
            val modality = request.getHeader(FormAttributes.MODALITY.value)
            RequestMetaData(
                deviceId, measurementId, osVersion, deviceType, applicationVersion,
                length, locationCount, startLocation, endLocation, modality, formatVersion
            )
        } catch (e: IllegalArgumentException) {
            throw InvalidMetaData("Data was not parsable!", e)
        } catch (e: NullPointerException) {
            throw InvalidMetaData("Data was not parsable!", e)
        }
    }

    /**
     * Extracts the content range information from the request header and checks it matches the body size information
     * from the header.
     *
     * @param request the request to check the header for
     * @param bodySize the number of bytes to be uploaded
     * @return the extracted content range information
     * @throws Unparsable if the content range does not match the body size information
     */
    @Throws(Unparsable::class)
    private fun contentRange(request: HttpServerRequest, bodySize: Long): ContentRange {

        // The client informs what data is attached: `bytes fromIndex-toIndex/totalBytes`
        val contentRangeString = request.getHeader("Content-Range")
        if (!contentRangeString.matches(Regex("bytes [0-9]+-[0-9]+/[0-9]+"))) {
            throw Unparsable(
                String.format(
                    "Content-Range request not supported: %s",
                    contentRangeString
                )
            )
        }
        val startingWithFrom = contentRangeString.substring(6)
        val dashPosition = startingWithFrom.indexOf('-')
        Validate.isTrue(dashPosition != -1)
        val from = startingWithFrom.substring(0, dashPosition)
        val startingWithTo = startingWithFrom.substring(dashPosition + 1)
        val slashPosition = startingWithTo.indexOf('/')
        Validate.isTrue(slashPosition != -1)
        val to = startingWithTo.substring(0, slashPosition)
        val total = startingWithTo.substring(slashPosition + 1)
        val contentRange = ContentRange(from, to, total)

        // Make sure content-length matches the content range (to-from+1)
        if (bodySize != contentRange.toIndex.toLong() - contentRange.fromIndex.toLong() + 1) {
            throw Unparsable(
                String.format(
                    "Upload size (%d) does not match content rang of header (%s)!",
                    bodySize, contentRange
                )
            )
        }
        return contentRange
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
        ctx: RoutingContext,
        request: HttpServerRequest,
        session: Session,
        user: User,
        contentRange: ContentRange,
        metaData: RequestMetaData
    ) {

        // Create temp file to accept binary
        val fileName = UUID.randomUUID().toString()
        val uploadFolder = FILE_UPLOADS_FOLDER.toFile()
        if (!uploadFolder.exists()) {
            Validate.isTrue(uploadFolder.mkdir())
        }
        val tempFile = Paths.get(uploadFolder.path, fileName).toAbsolutePath().toFile()

        // Bind session to this measurement and mark as "pre-request accepted"
        session.put(UPLOAD_PATH_FIELD, tempFile)
        acceptUpload(ctx, request, session, user, contentRange, tempFile, metaData)
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
        ctx: RoutingContext,
        request: HttpServerRequest,
        session: Session,
        user: User,
        contentRange: ContentRange,
        tempFile: File,
        metaData: RequestMetaData
    ) {
        val fs = ctx.vertx().fileSystem()
        request.pause()
        fs.open(tempFile.absolutePath, OpenOptions().setAppend(true)).onSuccess { asyncFile: AsyncFile ->
            request.resume()

            // Pipe body to reduce memory usage and store body of interrupted connections (to support resume)
            request.pipeTo(asyncFile).onSuccess { success: Void? ->
                // Check if the upload is complete or if this was just a chunk
                fs.props(tempFile.toString()).onSuccess { props: FileProps ->

                    // We could reuse information but to be sure we check the actual file size
                    val byteSize = props.size()
                    if (byteSize - 1 != contentRange.toIndex.toLong()) {
                        LOGGER.error(
                            String.format(
                                "Response: 500, Content-Range (%s) not matching file size (%d - 1)",
                                contentRange, byteSize
                            )
                        )
                        ctx.fail(500)
                        return@onSuccess
                    }

                    // This was not the final chunk of data
                    if (contentRange.toIndex.toLong() != contentRange.totalBytes.toLong() - 1) {
                        // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                        val range = String.format("bytes=0-%d", byteSize - 1)
                        LOGGER.debug(String.format("Response: 308, Range %s", range))
                        ctx.response().putHeader("Range", range)
                        ctx.response().putHeader("Content-Length", "0")
                        ctx.response().setStatusCode(StatusHandler.RESUME_INCOMPLETE).end()
                        return@onSuccess
                    }

                    // Persist data
                    val measurement =
                        Measurement(metaData, user.idString, tempFile)
                    storeToMongoDB(measurement, ctx)
                }.onFailure { failure: Throwable? ->
                    LOGGER.error("Response: 500, failed to read props from temp file")
                    ctx.fail(500, failure)
                }
            }.onFailure { failure: Throwable ->
                if (failure.javaClass == PayloadTooLarge::class.java) {
                    remove(session, tempFile) // client won't resume
                    LOGGER.error(String.format("Response: 422: %s", failure.message), failure)
                    ctx.fail(ENTITY_UNPARSABLE, failure.cause)
                    return@onFailure
                }
                // Not cleaning session/uploads to allow resume
                LOGGER.error(String.format("Response: 500: %s", failure.message), failure)
                ctx.fail(500, failure)
            }
        }.onFailure { failure: Throwable? ->
            LOGGER.error("Unable to open temporary file to stream request to!", failure)
            ctx.fail(500, failure)
        }
    }

    /**
     * Stores a [Measurement] to a Mongo database. This method never fails. If a failure occurs it is logged and
     * status code 422 is used for the response.
     *
     * @param measurement The measured data to write to the Mongo database
     * @param ctx The Vertx `RoutingContext` used to write the response
     */
    fun storeToMongoDB(measurement: Measurement, ctx: RoutingContext) {
        LOGGER.debug(
            "Inserted measurement with id {}:{}!", measurement.metaData.deviceIdentifier,
            measurement.metaData.measurementIdentifier
        )
        mongoClient.createDefaultGridFsBucketService().onSuccess { gridFs: MongoGridFsClient ->
            val fileSystem = ctx.vertx().fileSystem()
            val fileUpload = measurement.binary
            val openFuture = fileSystem.open(fileUpload.absolutePath, OpenOptions())
            val uploadFuture = openFuture.compose { file: AsyncFile? ->
                val options = GridFsUploadOptions()
                val metaData = measurement.toJson()
                options.metadata = JsonObject(metaData.toString())
                gridFs.uploadByFileNameWithOptions(file, fileUpload.name, options)
            }

            // Wait for all file uploads to complete
            uploadFuture.onSuccess { result: String? ->
                // Not removing session to allow the client to check the upload status if interrupted
                LOGGER.debug("Response: 201")
                clean(ctx.session(), measurement.binary)
                ctx.response().setStatusCode(201).end()
            }.onFailure { cause: Throwable? ->
                LOGGER.error("Unable to store file to MongoDatabase!", cause)
                ctx.fail(500, cause)
            }
        }.onFailure { cause: Throwable? ->
            LOGGER.error("Unable to open connection to Mongo Database!", cause)
            ctx.fail(500, cause)
        }
    }

    /**
     * Checks if the session is considered "valid", i.e. there was a pre-request which was accepted by the server with
     * the same identifiers as in this request.
     *
     * @param session the session to be checked
     * @param metaData the identifier of this request header
     * @throws IllegalSession if the device-/measurement id of this request does not match the one of the pre-request
     * @throws SessionExpired if the session is not found, e.g. because it expired
     */
    @Throws(IllegalSession::class, SessionExpired::class)
    private fun checkSessionValidity(session: Session, metaData: RequestMetaData) {

        // Ensure this session was accepted by PreRequestHandler and bound to this measurement
        val sessionMeasurementId = session.get<String>(PreRequestHandler.MEASUREMENT_ID_FIELD)
        val sessionDeviceId = session.get<String>(PreRequestHandler.DEVICE_ID_FIELD)
        if (sessionMeasurementId == null || sessionDeviceId == null) {
            throw SessionExpired("Mid/did missing, session maybe expired, request upload restart (404).")
        }
        if (sessionMeasurementId != metaData.measurementIdentifier) {
            throw IllegalSession(
                String.format(
                    "Unexpected measurement id: %s.",
                    sessionMeasurementId
                )
            )
        }
        if (sessionDeviceId != metaData.deviceIdentifier) {
            throw IllegalSession(
                String.format(
                    "Unexpected device id: %s.",
                    sessionDeviceId
                )
            )
        }
    }

    private fun remove(session: Session, upload: File) {
        remove(session)
        remove(upload)
    }

    private fun clean(session: Session, upload: File) {
        // not removing session
        session.remove<Any>(UPLOAD_PATH_FIELD)
        remove(upload)
    }

    private fun remove(upload: File) {
        val deleted = upload.delete()
        Validate.isTrue(deleted)
    }

    private fun remove(session: Session) {
        session.destroy()
    }

    /**
     * The content range information as transmitted by the request header.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 6.0.0
     */
    private class ContentRange(val fromIndex: String, val toIndex: String, val totalBytes: String) {
        override fun toString(): String {
            return "ContentRange{" +
                    "fromIndex='" + fromIndex + '\'' +
                    ", toIndex='" + toIndex + '\'' +
                    ", totalBytes='" + totalBytes + '\'' +
                    '}'
        }
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by
         * adapting the values in `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(MeasurementHandler::class.java)

        /**
         * HTTP status code to return when the client tries to resume an upload but the session has expired.
         */
        const val NOT_FOUND = 404

        /**
         * The field name for the session entry which contains the path of the temp file containing the upload binary.
         *
         *
         * This field is set in the [MeasurementHandler] to support resumable upload.
         */
        @JvmField
        val UPLOAD_PATH_FIELD = "upload-path"

        /**
         * The folder to cache file uploads until they are persisted.
         */
        @JvmField
        val FILE_UPLOADS_FOLDER = Path.of("file-uploads/")
    }
}
