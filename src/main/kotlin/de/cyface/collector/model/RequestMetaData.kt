/*
 * Copyright 2021-2022 Cyface GmbH
 *
 * This file is part of the Serialization.
 *
 * The Serialization is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Serialization is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Serialization. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.model

import de.cyface.collector.handler.FormAttributes
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.apache.commons.lang3.Validate
import java.io.Serializable
import java.nio.charset.Charset

/**
 * The metadata as transmitted in the request header or pre-request body.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 6.0.0
 * @property deviceIdentifier The worldwide unique identifier of the device uploading the data.
 * @property measurementIdentifier The device wide unique identifier of the uploaded measurement.
 * @property operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
 * @property deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
 * @property applicationVersion The version of the app that transmitted the measurement.
 * @property length The length of the measurement in meters.
 * @property locationCount The count of geolocations in the transmitted measurement.
 * @property startLocation The `GeoLocation` at the beginning of the track represented by the transmitted measurement.
 * @property endLocation The `GeoLocation` at the end of the track represented by the transmitted measurement.
 * @property modality The modality type used to capture the measurement.
 * @property formatVersion The format version of the upload file.
 */
data class RequestMetaData(
    val deviceIdentifier: String,
    val measurementIdentifier: String,
    val operatingSystemVersion: String,
    val deviceType: String,
    val applicationVersion: String,
    val length: Double,
    val locationCount: Long,
    val startLocation: GeoLocation?,
    val endLocation: GeoLocation?,
    val modality: String,
    val formatVersion: Int
) : Serializable {

    init {
        Validate.isTrue(
            deviceIdentifier.toByteArray(Charset.forName(DEFAULT_CHARSET)).size == UUID_LENGTH,
            "Field deviceId was not exactly 128 Bit, which is required for UUIDs!"
        )
        Validate.isTrue(
            deviceType.isNotEmpty() && deviceType.length <= MAX_GENERIC_METADATA_FIELD_LENGTH,
            "Field deviceType had an invalid length of %d!",
            deviceType.length.toLong()
        )
        Validate.isTrue(
            measurementIdentifier.isNotEmpty() && measurementIdentifier.length <= MAX_MEASUREMENT_ID_LENGTH,
            "Field measurementId had an invalid length of %d!",
            measurementIdentifier.length.toLong()
        )
        Validate.isTrue(
            operatingSystemVersion.isNotEmpty() &&
                operatingSystemVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH,
            "Field osVersion had an invalid length of %d!",
            operatingSystemVersion.length.toLong()
        )
        Validate.isTrue(
            applicationVersion.isNotEmpty() && applicationVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH,
            "Field applicationVersion had an invalid length of %d!",
            applicationVersion.length.toLong()
        )
        Validate.isTrue(
            length >= MINIMUM_TRACK_LENGTH,
            "Field length had an invalid value %d which is smaller then 0.0!",
            length
        )
        Validate.isTrue(
            locationCount >= MINIMUM_LOCATION_COUNT,
            "Field locationCount had an invalid value %d which is smaller then 0!",
            locationCount
        )
        Validate.isTrue(
            locationCount == MINIMUM_LOCATION_COUNT || startLocation != null,
            "Start location should only be defined if there is at least one location in the uploaded track!"
        )
        Validate.isTrue(
            locationCount == MINIMUM_LOCATION_COUNT || endLocation != null,
            "End location should only be defined if there is at least one location in the uploaded track!"
        )
        Validate.isTrue(
            modality.isNotEmpty() && modality.length <= MAX_GENERIC_METADATA_FIELD_LENGTH,
            "Field modality had an invalid length of %d!",
            modality.length.toLong()
        )
        Validate.isTrue(
            formatVersion == CURRENT_TRANSFER_FILE_FORMAT_VERSION,
            "Unsupported formatVersion: %d",
            formatVersion.toLong()
        )
    }

    /**
     * Transform this object into a generic JSON representation.
     */
    fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.DEVICE_ID.value, deviceIdentifier)
        ret.put(FormAttributes.MEASUREMENT_ID.value, measurementIdentifier)
        ret.put(FormAttributes.OS_VERSION.value, operatingSystemVersion)
        ret.put(FormAttributes.DEVICE_TYPE.value, deviceType)
        ret.put(FormAttributes.APPLICATION_VERSION.value, applicationVersion)
        ret.put(FormAttributes.LENGTH.value, length)
        ret.put(FormAttributes.LOCATION_COUNT.value, locationCount)
        if (startLocation != null) {
            ret.put("start", startLocation.geoJson())
        }
        if (endLocation != null) {
            ret.put("end", endLocation.geoJson())
        }
        ret.put(FormAttributes.MODALITY.value, modality)
        ret.put(FormAttributes.FORMAT_VERSION.value, formatVersion)
        return ret
    }

    /**
     * Transform this object into a valid Geo JSON representation.
     * This consists of the start location and the end location as a Geo JSON `MultiPoint` feature and all the
     * metadata as properties of that feature.
     *
     * See [GEO Json RFC 7946](https://www.rfc-editor.org/rfc/rfc7946) for additional detailed information.
     */
    fun toGeoJson(): JsonObject {
        val feature = JsonObject()
        val properties = JsonObject()
        val geometry = JsonObject()

        if (startLocation != null && endLocation != null) {
            val startCoordinates = JsonArray(mutableListOf(startLocation.longitude, startLocation.latitude))
            val endCoordinates = JsonArray(mutableListOf(endLocation.longitude, endLocation.latitude))
            val coordinates = JsonArray(mutableListOf(startCoordinates, endCoordinates))
            geometry
                .put("type", "MultiPoint")
                .put("coordinates", coordinates)
        } else {
            geometry
                .put("type", "MultiPoint")
                .put("coordinates", null)
        }

        properties.put(FormAttributes.DEVICE_ID.value, deviceIdentifier)
        properties.put(FormAttributes.MEASUREMENT_ID.value, measurementIdentifier)
        properties.put(FormAttributes.OS_VERSION.value, operatingSystemVersion)
        properties.put(FormAttributes.DEVICE_TYPE.value, deviceType)
        properties.put(FormAttributes.APPLICATION_VERSION.value, applicationVersion)
        properties.put(FormAttributes.LENGTH.value, length)
        properties.put(FormAttributes.LOCATION_COUNT.value, locationCount)
        properties.put(FormAttributes.MODALITY.value, modality)
        properties.put(FormAttributes.FORMAT_VERSION.value, formatVersion)

        feature
            .put("type", "Feature")
            .put("geometry", geometry)
            .put("properties", properties)

        val ret = JsonObject()
        ret.put("type", "FeatureCollection")
        ret.put("features", JsonArray().add(feature))
        return ret
    }

    /**
     * This class represents a geolocation at the start or end of a track.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 6.0.0
     * @property timestamp The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch).
     * @property latitude Geographical latitude in coordinates (decimal fraction)
     *                    ranging from -90° (south) to 90° (north).
     * @property longitude Geographical longitude in coordinates (decimal fraction)
     *                     ranging from -180° (west) to 180° (east).
     */
    data class GeoLocation(val timestamp: Long, val latitude: Double, val longitude: Double) {
        /**
         * Converts this location record into `JSON` which supports the mongoDB `GeoJSON` format:
         * https://docs.mongodb.com/manual/geospatial-queries/
         *
         * @return the converted location record as JSON
         */
        fun geoJson(): JsonObject {
            val ret = JsonObject()
            ret.put("timestamp", timestamp)
            val geometry = JsonObject()
                .put("type", "Point")
                .put("coordinates", JsonArray().add(longitude).add(latitude))
            ret.put("location", geometry)
            return ret
        }
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        private const val serialVersionUID = -1700430112854515404L

        /**
         * The length of a universal unique identifier.
         */
        private const val UUID_LENGTH = 36

        /**
         * The default char set to use for encoding and decoding strings transmitted as metadata.
         */
        private const val DEFAULT_CHARSET = "UTF-8"

        /**
         * Maximum size of a metadata field, with plenty space for future development.
         * This prevents attackers from putting arbitrary long data into these fields.
         */
        const val MAX_GENERIC_METADATA_FIELD_LENGTH = 30

        /**
         * The maximum length of the measurement identifier in characters (this is the amount of characters of
         * {@value Long#MAX_VALUE}).
         */
        private const val MAX_MEASUREMENT_ID_LENGTH = 20

        /**
         * The minimum length of a track stored with a measurement.
         */
        private const val MINIMUM_TRACK_LENGTH = 0.0

        /**
         * The minimum valid amount of locations stored inside a measurement.
         */
        private const val MINIMUM_LOCATION_COUNT = 0L

        /**
         * The current version of the transferred file. This is always specified by the first two bytes of the file
         * transferred and helps compatible APIs to process data from different client versions.
         */
        const val CURRENT_TRANSFER_FILE_FORMAT_VERSION = 3
    }
}
