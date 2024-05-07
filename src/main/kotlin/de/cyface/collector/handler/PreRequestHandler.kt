/*
 * Copyright 2021-2024 Cyface GmbH
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

import de.cyface.collector.handler.HTTPStatus.ENTITY_UNPARSABLE
import de.cyface.collector.handler.HTTPStatus.HTTP_CONFLICT
import de.cyface.collector.handler.HTTPStatus.OK
import de.cyface.collector.handler.HTTPStatus.PRECONDITION_FAILED
import de.cyface.collector.handler.HTTPStatus.SERVER_ERROR
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Handler
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * A handler for receiving HTTP POST requests on the "measurements" end point.
 * This end point tells the client if the upload may continue or should be skipped.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 6.0.0
 * @property storageService The service used to store data and retrieve information about stored data
 *                          by this application.
 * @property measurementLimit The maximal number of `Byte`s which may be uploaded in the upload request.
 * @property httpPath The path of the URL under which the Collector service is deployed. This is used to return the
 * correct "Location" header.
 */
class PreRequestHandler(
    private val storageService: DataStorageService,
    private val measurementLimit: Long,
    private val httpPath: String
) : Handler<RoutingContext> {

    override fun handle(ctx: RoutingContext) {
        LOGGER.info("Received new pre-request.")
        val request = ctx.request()
        val session = ctx.session()
        val metaData = ctx.body().asJsonObject()
        try {
            bodySize(request.headers(), measurementLimit, X_UPLOAD_CONTENT_LENGTH_FIELD)
            check(metaData)
            check(session)

            // Check if measurement already exists in database
            val measurementId = metaData.getString(FormAttributes.MEASUREMENT_ID.value)
            val deviceId = metaData.getString(FormAttributes.DEVICE_ID.value)
            if (measurementId == null || deviceId == null) {
                throw InvalidMetaData("Data incomplete!")
            }

            val isStoredResult = storageService.isStored(deviceId, measurementId.toLong())
            isStoredResult.onSuccess { measurementExists ->
                if (measurementExists) {
                    LOGGER.debug("Response: 409, measurement already exists, no upload needed")
                    ctx.response().setStatusCode(HTTP_CONFLICT).end()
                } else {
                    // Bind session to this measurement and mark as "pre-request accepted"
                    session.put(MEASUREMENT_ID_FIELD, measurementId)
                    session.put(DEVICE_ID_FIELD, deviceId)

                    // Google uses the `Location` format:
                    // `https://host/endpoint?uploadType=resumable&upload_id=SESSION_ID`
                    // To use the Vert.X session parsing we use:
                    // `https://host/endpoint?uploadType=resumable&upload_id=(SESSION_ID)"
                    val requestUri = URL(request.absoluteURI())
                    val protocol = request.getHeader("X-Forwarded-Proto")
                    val locationUri = locationUri(requestUri, protocol, session.id())
                    LOGGER.debug("Response 200, Location: $locationUri")
                    ctx.response()
                        .putHeader("Location", locationUri.toURL().toExternalForm())
                        .putHeader("Content-Length", "0")
                        .setStatusCode(OK).end()
                }
            }
            isStoredResult.onFailure { ctx.fail(SERVER_ERROR) }
        } catch (e: InvalidMetaData) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: Unparsable) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: IllegalSession) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: PayloadTooLarge) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: SkipUpload) {
            // Ask the client to skip the upload, e.g. b/c of geo-fencing, deprecated Binary, missing locations, ...
            // We can add information to the response body to distinguish different causes.
            ctx.fail(PRECONDITION_FAILED, e)
        }
    }

    /**
     * Checks that no pre-existing session was passed in the pre-request.
     *
     * @param session the session to check
     * @throws IllegalSession if an existing session was passed
     */
    @Throws(IllegalSession::class)
    private fun check(session: Session) {
        // The purpose of the `pre-request` is to generate an upload session for `upload requests`.
        // Thus, we're expecting that the `SessionHandler` automatically created a new session for this pre-request.
        val measurementId = session.get<Any>(MEASUREMENT_ID_FIELD)
        val deviceId = session.get<Any>(DEVICE_ID_FIELD)
        if (measurementId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected measurement id: %s.", measurementId))
        }
        if (deviceId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected device id: %s.", deviceId))
        }
    }

    /**
     * Assembles the `Uri` for the `Location` header required by the client who sent the upload request.
     *
     * This contains the upload session id which is needed to start an upload.
     *
     * @param requestUri the `Uri` to which the upload request was sent.
     * @param protocol the protocol used in the upload request as defined in the request header `X-Forwarded-Proto`
     * @param sessionId the upload session id to be added to the `Location` header assembled.
     * @return The assembled `Location` `Uri` to be returned to the client
     */
    @Deprecated(
        "This requires knowledge about the applications deployment behind a proxy and thus should " +
            "not be used. Instead make the location header relative and avoid rewriting this URL here."
    )
    private fun locationUri(requestUri: URL, protocol: String?, sessionId: String): URI {
        // Our current setup forwards https requests to http internally. As the `Location` returned is automatically
        // used by the Google Client API library for the upload request we make sure the original protocol use returned.
        val scheme = if (protocol != null) protocol else requestUri.toURI().scheme
        // The Google Client API library automatically adds the parameter `uploadType` to the Uri. As our upload API
        // offers an endpoint address in the format `measurement/(SID)` we remove the `uploadType` parameter.
        // We don't need to process the `uploadType` as we're only offering one upload type: resumable.
        var query = requestUri.query?.replace("uploadType=resumable", "")
        query = if (query != null && query.isEmpty()) { null } else { query }

        var strippedPath = if (httpPath.startsWith("/")) {
            httpPath.subSequence(1..<httpPath.length)
        } else {
            httpPath
        }
        strippedPath = if (strippedPath.endsWith("/")) {
            strippedPath.subSequence(0..<strippedPath.length - 1)
        } else {
            strippedPath
        }
        val path = if (strippedPath.isEmpty()) {
            "${requestUri.path}/($sessionId)/"
        } else {
            "/$strippedPath${requestUri.path}/($sessionId)/"
        }

        return URI(
            scheme,
            null,
            requestUri.host,
            requestUri.port,
            path,
            query,
            null
        )
    }

    /**
     * Checks the metadata in the {@param body}.
     *
     * @param body The request body to check for the expected metadata fields.
     * @throws InvalidMetaData If the metadata is in an invalid format.
     * @throws SkipUpload If the server is not interested in the data.
     * @throws Unparsable E.g. if there is a syntax error in the body.
     */
    @Throws(InvalidMetaData::class, SkipUpload::class, Unparsable::class)
    private fun check(body: JsonObject) {
        try {
            val locationCount = body.getString(FormAttributes.LOCATION_COUNT.value).toLong()
            if (locationCount < MINIMUM_LOCATION_COUNT) {
                throw SkipUpload(String.format(Locale.ENGLISH, "Too few location points %s", locationCount))
            }
            val formatVersion = body.getString(FormAttributes.FORMAT_VERSION.value).toInt()
            if (formatVersion != CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
                throw SkipUpload(String.format(Locale.ENGLISH, "Unsupported format version: %s", formatVersion))
            }
            val startLocationLat = body.getString(FormAttributes.START_LOCATION_LAT.value)
            val startLocationLon = body.getString(FormAttributes.START_LOCATION_LON.value)
            val startLocationTs = body.getString(FormAttributes.START_LOCATION_TS.value)
            val endLocationLat = body.getString(FormAttributes.END_LOCATION_LAT.value)
            val endLocationLon = body.getString(FormAttributes.END_LOCATION_LON.value)
            val endLocationTs = body.getString(FormAttributes.END_LOCATION_TS.value)
            if (startLocationLat == null) throw InvalidMetaData("Data incomplete startLocationLat was null!")
            if (startLocationLon == null) throw InvalidMetaData("Data incomplete startLocationLon was null!")
            if (startLocationTs == null) throw InvalidMetaData("Data incomplete startLocationTs was null!")
            if (endLocationLat == null) throw InvalidMetaData("Data incomplete endLocationLat was null!")
            if (endLocationLon == null) throw InvalidMetaData("Data incomplete endLocationLon was null!")
            if (endLocationTs == null) throw InvalidMetaData("Data incomplete endLocationTs was null!")
            val measurementId = body.getString(FormAttributes.MEASUREMENT_ID.value)
            val deviceId = body.getString(FormAttributes.DEVICE_ID.value)
            if (measurementId == null || deviceId == null) {
                throw InvalidMetaData("Data incomplete!")
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidMetaData("Data was not parsable!", e)
        } catch (e: NullPointerException) {
            throw InvalidMetaData("Data was not parsable!", e)
        }
    }

    companion object {
        /**
         * The `Logger` used for objects of this class. Configure it by changing the settings in
         * `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(PreRequestHandler::class.java)

        /**
         * The minimum amount of location points required to accept an upload.
         */
        const val MINIMUM_LOCATION_COUNT = 2

        /**
         * The current version of the transferred file. This is always specified by the first two bytes of the file
         * transferred and helps compatible APIs to process data from different client versions.
         */
        const val CURRENT_TRANSFER_FILE_FORMAT_VERSION = 3

        /**
         * The header field which contains the number of bytes of the "requested" upload.
         */
        private const val X_UPLOAD_CONTENT_LENGTH_FIELD = "x-upload-content-length"

        /**
         * The field name for the session entry which contains the measurement id.
         *
         *
         * This field is set in the [PreRequestHandler] to make sure sessions are bound to measurements and uploads
         * are only accepted with an accepted pre request.
         */
        const val MEASUREMENT_ID_FIELD = "measurement-id"

        /**
         * The field name for the session entry which contains the device id.
         *
         *
         * This field is set in the [PreRequestHandler] to make sure sessions are bound to measurements and uploads
         * are only accepted with an accepted pre request.
         */
        const val DEVICE_ID_FIELD = "device-id"

        /**
         * Checks if the information about the upload size in the header exceeds the {@param measurementLimit}.
         *
         * @param headers The header to check.
         * @param measurementLimit The maximal number of `Byte`s which may be uploaded in the upload request.
         * @param uploadLengthField The name of the header field to check.
         * @return the number of bytes to be uploaded
         * @throws Unparsable If the header is missing the expected field about the upload size.
         * @throws PayloadTooLarge If the requested upload is too large.
         */
        @Throws(Unparsable::class, PayloadTooLarge::class)
        fun bodySize(headers: MultiMap, measurementLimit: Long, uploadLengthField: String?): Long {
            if (!headers.contains(uploadLengthField)) {
                throw Unparsable(String.format(Locale.ENGLISH, "The header is missing the field %s", uploadLengthField))
            }
            val uploadLengthString = headers[uploadLengthField]
            return try {
                val uploadLength = uploadLengthString.toLong()
                if (uploadLength > measurementLimit) {
                    throw PayloadTooLarge(
                        String.format(
                            Locale.ENGLISH,
                            "Upload size in the pre-request (%d) is too large, limit is %d bytes.",
                            uploadLength,
                            measurementLimit
                        )
                    )
                }
                uploadLength
            } catch (e: NumberFormatException) {
                throw Unparsable(
                    String.format(
                        Locale.ENGLISH,
                        "The header field %s is unparsable: %s",
                        uploadLengthField,
                        uploadLengthString
                    )
                )
            }
        }
    }
}
