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
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject

/**
 * Interface for services which check metadata from upload requests and their pre-requests.
 *
 * @author Armin Schnabel
 */
interface MetaDataService {

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
            throw SkipUpload(e)
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
        } catch (e: TooFewLocations) {
            throw SkipUpload(e)
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
         * The minimum amount of location points required to accept an upload.
         */
        const val MINIMUM_LOCATION_COUNT = 2

        /**
         * The current version of the transferred file. This is always specified by the first two bytes of the file
         * transferred and helps compatible APIs to process data from different client versions.
         */
        const val CURRENT_TRANSFER_FILE_FORMAT_VERSION = 3
    }
}
