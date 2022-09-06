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

import de.cyface.api.model.User
import de.cyface.collector.handler.HTTPStatus.ENTITY_UNPARSABLE
import de.cyface.collector.handler.HTTPStatus.NOT_FOUND
import de.cyface.collector.handler.HTTPStatus.PRECONDITION_FAILED
import de.cyface.collector.handler.HTTPStatus.RESUME_INCOMPLETE
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.UnexpectedContentRange
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.ContentRange
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.streams.Pipe
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.stream.Collectors

/**
 * A handler for receiving HTTP PUT requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @property storageService A service used to store the received data to some persistent data store.
 * @property payloadLimit The maximum number of `Byte`s which may be uploaded.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
class MeasurementHandler(
    private val storageService: DataStorageService,
    private val payloadLimit: Long
) : Handler<RoutingContext> {

    init {
        Validate.isTrue(payloadLimit > 0)
    }

    /*override fun handleAuthorizedRequest(
        ctx: RoutingContext,
        users: Set<User>,
        header: MultiMap
    ) */
    override fun handle(ctx: RoutingContext) {
        LOGGER.info("Received new measurement request.")
        val request = ctx.request()
        val session = ctx.session()
        try {
            // Load authenticated user
            val username = ctx.user().principal().getString("username")
            val users = ctx.get<Set<User>?>("accessible-users")
            if (users == null) {
                ctx.response().setStatusCode(401).end()
                return
            }
            val matched = users.stream().filter { u: User -> u.name == username }.collect(Collectors.toList())
            Validate.isTrue(matched.size == 1)
            val loggedInUser = matched.stream().findFirst().get() // Make sure it's the matched user
            val bodySize = PreRequestHandler.bodySize(request.headers(), payloadLimit, "content-length")
            val metaData = metaData(request)
            checkSessionValidity(session, metaData)

            // Handle upload status request
            if (bodySize == 0L) {
                return ctx.next()
            }

            // Handle first chunk
            val contentRange = contentRange(request, bodySize)
            val checkResult = check(session, loggedInUser, request.pipe(), contentRange, metaData)
            checkResult.onSuccess { result ->
                when(result.type) {
                    StatusType.INCOMPLETE -> {
                        val byteSize = result.byteSize
                        val range = String.format("bytes=0-%d", byteSize - 1)
                        ctx.response().putHeader("Range", range)
                        ctx.response().putHeader("Content-Length", "0")
                        ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                    }
                    StatusType.COMPLETE -> {
                        // not removing session
                        session.remove<Any>(UPLOAD_PATH_FIELD)
                        storageService.clean(result.uploadIdentifier).onSuccess {
                                ctx.response().setStatusCode(201).end()
                            }.onFailure { cause ->
                                ctx.fail(cause)
                            }
                    }
                }
            }
            checkResult.onFailure(MeasurementFailureHandler(ctx))
        } catch (e: InvalidMetaData) {
            LOGGER.error("Response: 422", e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: Unparsable) {
            LOGGER.error("Response: 422", e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: IllegalSession) {
            LOGGER.error("Response: 422", e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: SessionExpired) {
            LOGGER.warn("Response: 404", e)
            ctx.response().setStatusCode(NOT_FOUND).end() // client sends a new pre-request for this upload
        } catch (e: SkipUpload) {
            LOGGER.debug(e.message, e)
            session.destroy() // client won't resume
            ctx.fail(PRECONDITION_FAILED, e)
        } catch (e: PayloadTooLarge) {
            LOGGER.error("Response: 422", e)
            // client won't resume
            session.destroy()
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: RuntimeException) {
            ctx.fail(e)
        }
    }

    private fun check(session: Session, user: User, pipe: Pipe<Buffer>, contentRange: ContentRange, metaData: RequestMetaData): Future<Status> {
        val ret = Promise.promise<Status>()
        val uploadIdentifier = session.get<UUID?>(UPLOAD_PATH_FIELD)

        if (uploadIdentifier == null && contentRange.fromIndex != "0") {
           // I.e. the server received data in a previous request but now cannot find the `path` session value.
           // Unsure when this can happen. Not accepting the data. Asking to restart upload (`404`).
           val message = String.format("Response: 404, path is null and unexpected content range: %s", contentRange)
           LOGGER.warn(message)
           ret.fail(UnexpectedContentRange(message))
        } else if(uploadIdentifier!=null && contentRange.fromIndex == "0") {
            // Server received data in a previous request but the chunk file was probably cleaned by cleaner task.
            // Can't return 308. Google API client lib throws `Preconditions` on subsequent chunk upload.
            // This makes sense as the server reported before that bytes (>0) were received.
            val message = String.format("Response: 404, Unexpected content range: %s", contentRange)
            LOGGER.warn(message)
            ret.fail(UnexpectedContentRange(message))
        } else if(uploadIdentifier==null) {
            // Create temp file to accept binary
            val newUploadIdentifier = UUID.randomUUID()

            // Bind session to this measurement and mark as "pre-request accepted"
            session.put(UPLOAD_PATH_FIELD, newUploadIdentifier)
            val acceptUploadResult = storageService.store(pipe, user, contentRange, newUploadIdentifier, metaData)
            acceptUploadResult.onSuccess { result -> ret.complete(result) }
            acceptUploadResult.onFailure { cause -> ret.fail(cause) }
        } else {

            // Search for previous upload chunk
            storageService.bytesUploaded(uploadIdentifier).onSuccess {byteSize ->
                // Wrong chunk uploaded
                if (contentRange.fromIndex != byteSize.toString()) {
                    // Ask client to resume from the correct position
                    val range = String.format("bytes=0-%d", byteSize - 1)
                    LOGGER.debug("Response: 308, Range {} (partial data)", range)
                    ret.complete(Status(uploadIdentifier, StatusType.INCOMPLETE, byteSize))
                } else {
                    val acceptUploadResult = storageService.store(pipe, user, contentRange, uploadIdentifier, metaData)
                    acceptUploadResult.onSuccess { result -> ret.complete(result) }
                    acceptUploadResult.onFailure { cause -> ret.fail(cause) }
                }
            }.onFailure {
                session.remove<Any>(UPLOAD_PATH_FIELD) // was linked to non-existing file

                // Create new upload identifier for this upload
                val newUploadIdentifier = UUID.randomUUID()

                // Bind session to this measurement and mark as "pre-request accepted"
                session.put(UPLOAD_PATH_FIELD, newUploadIdentifier)
                val acceptUploadResult = storageService.store(pipe, user, contentRange, newUploadIdentifier, metaData)
                acceptUploadResult.onSuccess { result -> ret.complete(result) }
                acceptUploadResult.onFailure { cause -> ret.fail(cause) }
            }
        }

        return ret.future()
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

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by
         * adapting the values in `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(MeasurementHandler::class.java)

        /**
         * The field name for the session entry which contains the path of the temp file containing the upload binary.
         *
         *
         * This field is set in the [MeasurementHandler] to support resumable upload.
         */
        const val UPLOAD_PATH_FIELD = "upload-path"
    }
}
