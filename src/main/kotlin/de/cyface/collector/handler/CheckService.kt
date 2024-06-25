/*
 * Copyright 2024 Cyface GmbH
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

import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Session
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Interface for services which check upload requests and their pre-requests.
 *
 * @author Armin Schnabel
 */
interface CheckService {
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
    fun checkBodySize(headers: MultiMap, measurementLimit: Long, uploadLengthField: String): Long {
        val uploadLengthString = headers[uploadLengthField]
            ?: throw Unparsable(
                String.format(
                    Locale.ENGLISH,
                    "The header is missing the field %s",
                    uploadLengthField
                )
            )

        val uploadLength = try {
            uploadLengthString.toLong()
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

        return uploadLength
    }

    /**
     * Checks that no pre-existing session was passed in the pre-request.
     *
     * The purpose of the `pre-request` is to generate an upload session for `upload requests`.
     * Thus, we're expecting that the `SessionHandler` automatically created a new session for this pre-request.
     *
     * @param session the session to check
     * @throws IllegalSession if an existing session was passed
     */
    @Throws(IllegalSession::class)
    fun checkSession(session: Session)

    /**
     * Checks if the file to be uploaded is already stored in the database.
     *
     * This can include checking for the existence of the measurement itself or also for the existence of attachments.
     *
     * @param identifier The id of the transmitted file to check if it already exists.
     * @return A future that will be completed with true if the check are successful. Depending on the implementation
     * this might mean that the measurement does or does not yet exist or that the attachment does not yet exist.
     */
    fun checkConflict(identifier: RequestMetaData.MeasurementIdentifier): Future<Boolean>

    /**
     * Extracts the metadata from the request header.
     *
     * @param headers the header to extract the data from
     * @return the extracted metadata
     * @throws SkipUpload when the server is not interested in the uploaded data
     * @throws InvalidMetaData when the request is missing metadata fields
     */
    @Throws(InvalidMetaData::class, SkipUpload::class)
    fun <T : RequestMetaData.MeasurementIdentifier> metaData(headers: MultiMap): RequestMetaData<T> {
        try {
            val identifier = identifier(headers) as T

            // Device metadata
            val osVersion = headers.get(FormAttributes.OS_VERSION.value)
            val deviceType = headers.get(FormAttributes.DEVICE_TYPE.value)

            // Application metadata
            val appVersion = headers.get(FormAttributes.APPLICATION_VERSION.value)
            val formatVersion = headers.get(FormAttributes.FORMAT_VERSION.value).toInt()

            // Measurement metadata
            val length = headers.get(FormAttributes.LENGTH.value).toDouble()
            val locationCount = headers.get(FormAttributes.LOCATION_COUNT.value).toLong()
            val startLocationLat = headers.get(FormAttributes.START_LOCATION_LAT.value)
            val startLocationLon = headers.get(FormAttributes.START_LOCATION_LON.value)
            val startLocationTs = headers.get(FormAttributes.START_LOCATION_TS.value)
            val endLocationLat = headers.get(FormAttributes.END_LOCATION_LAT.value)
            val endLocationLon = headers.get(FormAttributes.END_LOCATION_LON.value)
            val endLocationTs = headers.get(FormAttributes.END_LOCATION_TS.value)
            val startLocation = geoLocation(startLocationTs, startLocationLat, startLocationLon)
            val endLocation = geoLocation(endLocationTs, endLocationLat, endLocationLon)
            val modality = headers.get(FormAttributes.MODALITY.value)

            val attachmentMetaData = attachmentMetaData(headers)

            return RequestMetaData(
                identifier,
                RequestMetaData.DeviceMetaData(osVersion, deviceType),
                RequestMetaData.ApplicationMetaData(appVersion, formatVersion),
                RequestMetaData.MeasurementMetaData(length, locationCount, startLocation, endLocation, modality),
                attachmentMetaData,
            )
        } catch (e: DeprecatedFormatVersion) {
            throw SkipUpload(e) // FIXME: should maybe be outside?
        } catch (e: UnknownFormatVersion) {
            throw InvalidMetaData(e)
        } catch (e: TooFewLocations) {
            throw SkipUpload(e)
        } catch (e: RuntimeException) {
            throw InvalidMetaData("Data was not parsable!", e)
        }
    }

    /**
     * Extracts the metadata from the request header.
     *
     * @param body The request body to extract the metadata from.
     * @return the extracted metadata
     * @throws SkipUpload when the server is not interested in the uploaded data
     * @throws InvalidMetaData when the request is missing metadata fields
     * @throws Unparsable E.g. if there is a syntax error in the body.
     */
    @Throws(InvalidMetaData::class, SkipUpload::class, Unparsable::class)
    fun <T : RequestMetaData.MeasurementIdentifier> metaData(body: JsonObject): RequestMetaData<T> {
        try {
            val identifier = identifier(body) as T

            // Device metadata
            val osVersion = body.getString(FormAttributes.OS_VERSION.value)
            val deviceType = body.getString(FormAttributes.DEVICE_TYPE.value)

            // Application metadata
            val appVersion = body.getString(FormAttributes.APPLICATION_VERSION.value)
            val formatVersion = body.getString(FormAttributes.FORMAT_VERSION.value).toInt()

            // Measurement metadata
            val length = body.getString(FormAttributes.LENGTH.value).toDouble()
            val locationCount = body.getString(FormAttributes.LOCATION_COUNT.value).toLong()
            val startLocationLat = body.getString(FormAttributes.START_LOCATION_LAT.value)
            val startLocationLon = body.getString(FormAttributes.START_LOCATION_LON.value)
            val startLocationTs = body.getString(FormAttributes.START_LOCATION_TS.value)
            val endLocationLat = body.getString(FormAttributes.END_LOCATION_LAT.value)
            val endLocationLon = body.getString(FormAttributes.END_LOCATION_LON.value)
            val endLocationTs = body.getString(FormAttributes.END_LOCATION_TS.value)
            val startLocation = geoLocation(startLocationTs, startLocationLat, startLocationLon)
            val endLocation = geoLocation(endLocationTs, endLocationLat, endLocationLon)
            val modality = body.getString(FormAttributes.MODALITY.value)

            val attachmentMetaData = attachmentMetaData(body)

            return RequestMetaData(
                identifier,
                RequestMetaData.DeviceMetaData(osVersion, deviceType),
                RequestMetaData.ApplicationMetaData(appVersion, formatVersion),
                RequestMetaData.MeasurementMetaData(length, locationCount, startLocation, endLocation, modality),
                attachmentMetaData,
            )
        } catch (e: IllegalArgumentException) {
            throw InvalidMetaData("Data was not parsable!", e)
        } catch (e: NullPointerException) {
            throw InvalidMetaData("Data was not parsable!", e)
        }
    }

    /**
     * Extracts the upload file specific identifier from the request body.
     *
     * @param metaData The request body containing the metadata.
     * @return The identifier.
     */
    fun identifier(metaData: JsonObject): RequestMetaData.MeasurementIdentifier

    /**
     * Extracts the upload file specific identifier from the request header.
     *
     * @param headers The request header containing the metadata.
     * @return The identifier.
     */
    fun identifier(headers: MultiMap): RequestMetaData.MeasurementIdentifier

    /**
     * Extracts the attachment specific metadata from the request body.
     *
     * @param body The request body containing the metadata.
     * @return The extracted metadata.
     */
    fun attachmentMetaData(body: JsonObject): RequestMetaData.AttachmentMetaData {
        val logCount = body.getString(FormAttributes.LOG_COUNT.value)
        val imageCount = body.getString(FormAttributes.IMAGE_COUNT.value)
        val videoCount = body.getString(FormAttributes.VIDEO_COUNT.value)
        val filesSize = body.getString(FormAttributes.FILES_SIZE.value)
        return attachmentMetaData(logCount, imageCount, videoCount, filesSize)
    }

    /**
     * Extracts the attachment specific metadata from the request header.
     *
     * @param headers The request header containing the metadata.
     * @return The extracted metadata.
     */
    fun attachmentMetaData(headers: MultiMap): RequestMetaData.AttachmentMetaData {
        val logCount = headers.get(FormAttributes.LOG_COUNT.value)
        val imageCount = headers.get(FormAttributes.IMAGE_COUNT.value)
        val videoCount = headers.get(FormAttributes.VIDEO_COUNT.value)
        val filesSize = headers.get(FormAttributes.FILES_SIZE.value)
        return attachmentMetaData(logCount, imageCount, videoCount, filesSize)
    }

    fun attachmentMetaData(logCount: String?, imageCount: String?, videoCount: String?, filesSize: String?):
            RequestMetaData.AttachmentMetaData

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
    fun <T : RequestMetaData.MeasurementIdentifier> checkSessionValidity(session: Session, metaData: RequestMetaData<T>)

    /**
     * Extracts the content range information from the request header and checks it matches the body size information
     * from the header.
     *
     * @param headers the request header to check
     * @param bodySize the number of bytes to be uploaded
     * @return the extracted content range information
     * @throws Unparsable if the content range does not match the body size information
     */
    @Throws(Unparsable::class)
    fun contentRange(headers: MultiMap, bodySize: Long): ContentRange {
        // The client informs what data is attached: `bytes fromIndex-toIndex/totalBytes`
        val contentRangeString = headers.get("Content-Range")
        val contentRange = ContentRange.fromHTTPHeader(contentRangeString)

        // Make sure content-length matches the content range (to-from+1)
        val sizeAccordingToContentRange = contentRange.toIndex - contentRange.fromIndex + 1L
        if (bodySize != sizeAccordingToContentRange) {
            throw Unparsable(
                String.format(
                    Locale.ENGLISH,
                    "Upload size (%d) does not match content rang of header (%s)!",
                    bodySize,
                    contentRange
                )
            )
        }
        return contentRange
    }

    /**
     * Checks the `Content-Range` field in the request.
     *
     * Only requests are accepted where the client knows the file size, i.e. `Content-Range` headers with
     * `bytes *\/SIZE` but not `bytes *\/\*` (ignore the escape-characters `\` in the documentation).
     *
     * @param headers the request header to check
     */
    fun checkContentRange(headers: MultiMap): Boolean {
        val rangeRequest = headers.get("Content-Range")
        if (!rangeRequest.matches(RANGE_VALUE_CHECK)) {
            LOGGER.error("Content-Range request not supported: {}", rangeRequest)
            return false
        }
        return true
    }

    fun geoLocation(timestamp: String?, latitude: String?, longitude: String?):
            RequestMetaData.MeasurementMetaData.GeoLocation? {
        return if (timestamp != null && latitude != null && longitude != null) {
            RequestMetaData.MeasurementMetaData.GeoLocation(
                timestamp.toLong(),
                latitude.toDouble(),
                longitude.toDouble(),
            )
        } else {
            null
        }
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by
         * adapting the values in `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(CheckService::class.java)

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
         * A regular expression to check the range HTTP header parameter value.
         */
        private val RANGE_VALUE_CHECK = "bytes \\*/[0-9]+".toRegex()
    }
}
