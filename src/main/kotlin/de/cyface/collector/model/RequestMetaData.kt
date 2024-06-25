/*
 * Copyright 2021-2024 Cyface GmbH
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
import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.model.RequestMetaData.MeasurementMetaData.GeoLocation
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.Serializable
import java.nio.charset.Charset

/**
 * The metadata as transmitted in the file upload request header or pre-request body.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @property identifier The identifier which identifies the data object transmitted.
 */
data class RequestMetaData<T : RequestMetaData.MeasurementIdentifier>(
    val identifier: T,
    val deviceMetaData: DeviceMetaData,
    val applicationMetaData: ApplicationMetaData,
    val measurementMetaData: MeasurementMetaData,
    val attachmentMetaData: AttachmentMetaData,
) : Serializable {

    /**
     * Transform this object into a generic JSON representation.
     */
    fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.DEVICE_ID.value, identifier.deviceId)
        ret.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementId)
        ret.put(FormAttributes.OS_VERSION.value, deviceMetaData.operatingSystemVersion)
        ret.put(FormAttributes.DEVICE_TYPE.value, deviceMetaData.deviceType)
        ret.put(FormAttributes.APPLICATION_VERSION.value, applicationMetaData.applicationVersion)
        ret.put(FormAttributes.LENGTH.value, measurementMetaData.length)
        ret.put(FormAttributes.LOCATION_COUNT.value, measurementMetaData.locationCount)
        if (measurementMetaData.startLocation != null) {
            ret.put("start", measurementMetaData.startLocation.geoJson())
        }
        if (measurementMetaData.endLocation != null) {
            ret.put("end", measurementMetaData.endLocation.geoJson())
        }
        ret.put(FormAttributes.MODALITY.value, measurementMetaData.modality)
        ret.put(FormAttributes.FORMAT_VERSION.value, applicationMetaData.formatVersion)
        ret.put(FormAttributes.LOG_COUNT.value, attachmentMetaData.logCount)
        ret.put(FormAttributes.IMAGE_COUNT.value, attachmentMetaData.imageCount)
        ret.put(FormAttributes.VIDEO_COUNT.value, attachmentMetaData.videoCount)
        ret.put(FormAttributes.FILES_SIZE.value, attachmentMetaData.filesSize)
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

        if (measurementMetaData.startLocation != null && measurementMetaData.endLocation != null) {
            val startCoordinates = JsonArray(
                mutableListOf(
                    measurementMetaData.startLocation.longitude,
                    measurementMetaData.startLocation.latitude
                )
            )
            val endCoordinates = JsonArray(
                mutableListOf(
                    measurementMetaData.endLocation.longitude,
                    measurementMetaData.endLocation.latitude
                )
            )
            val coordinates = JsonArray(mutableListOf(startCoordinates, endCoordinates))
            geometry
                .put("type", "MultiPoint")
                .put("coordinates", coordinates)
        } else {
            geometry
                .put("type", "MultiPoint")
                .put("coordinates", null)
        }

        properties.put(FormAttributes.DEVICE_ID.value, identifier.deviceId)
        properties.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementId)
        properties.put(FormAttributes.OS_VERSION.value, deviceMetaData.operatingSystemVersion)
        properties.put(FormAttributes.DEVICE_TYPE.value, deviceMetaData.deviceType)
        properties.put(FormAttributes.APPLICATION_VERSION.value, applicationMetaData.applicationVersion)
        properties.put(FormAttributes.LENGTH.value, measurementMetaData.length)
        properties.put(FormAttributes.LOCATION_COUNT.value, measurementMetaData.locationCount)
        properties.put(FormAttributes.MODALITY.value, measurementMetaData.modality)
        properties.put(FormAttributes.FORMAT_VERSION.value, applicationMetaData.formatVersion)
        properties.put(FormAttributes.LOG_COUNT.value, attachmentMetaData.logCount)
        properties.put(FormAttributes.IMAGE_COUNT.value, attachmentMetaData.imageCount)
        properties.put(FormAttributes.VIDEO_COUNT.value, attachmentMetaData.videoCount)
        properties.put(FormAttributes.FILES_SIZE.value, attachmentMetaData.filesSize)

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
     * An identifier for a transmitted measurement file.
     *
     * @author Armin Schnabel
     * @property deviceId The world-unique identifier of the device which collected the data.
     * @property measurementId The device-unique identifier of the measurement transmitted.
     */
    open class MeasurementIdentifier(
        val deviceId: String,
        val measurementId: String,
    ) : Serializable {

        init {
            require(deviceId.toByteArray(Charset.forName(DEFAULT_CHARSET)).size == UUID_LENGTH) {
                "Field deviceId was not exactly 128 Bit, which is required for UUIDs!"
            }
            require(measurementId.isNotEmpty() && measurementId.length <= MAX_ID_LENGTH) {
                "Field measurementId had an invalid length of ${measurementId.length.toLong()}"
            }
        }

        companion object {
            /**
             * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
             */
            @Suppress("ConstPropertyName")
            private const val serialVersionUID = 1L

            /**
             * The length of a universal unique identifier.
             */
            const val UUID_LENGTH = 36

            /**
             * The default char set to use for encoding and decoding strings transmitted as metadata.
             */
            const val DEFAULT_CHARSET = "UTF-8"

            /**
             * The maximum length of the measurement or attachment identifier in characters (this is the amount of
             * characters of {@value Long#MAX_VALUE}).
             */
            const val MAX_ID_LENGTH = 20
        }

        override fun toString(): String {
            return "MeasurementIdentifier(deviceId='$deviceId', measurementId='$measurementId')"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MeasurementIdentifier

            if (deviceId != other.deviceId) return false
            if (measurementId != other.measurementId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = deviceId.hashCode()
            result = 31 * result + measurementId.hashCode()
            return result
        }
    }

    /**
     * An identifier for a transmitted attachment file.
     *
     * @author Armin Schnabel
     * @property deviceId The world-unique identifier of the device which collected the data.
     * @property measurementId The device-unique identifier of the measurement transmitted.
     * @property attachmentId The device-unique identifier of the attachment file transmitted.
     */
    class AttachmentIdentifier(
        deviceId: String,
        measurementId: String,
        val attachmentId: String,
    ) : MeasurementIdentifier(deviceId, measurementId) {
        init {
            require(attachmentId.isNotEmpty() && attachmentId.length <= MAX_ID_LENGTH) {
                "Field attachmentId had an invalid length of ${attachmentId.length.toLong()}"
            }
        }

        companion object {
            /**
             * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
             */
            @Suppress("ConstPropertyName")
            private const val serialVersionUID = 1L
        }

        override fun toString(): String {
            return "AttachmentIdentifier(attachmentId='$attachmentId')"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false

            other as AttachmentIdentifier

            return attachmentId == other.attachmentId
        }

        override fun hashCode(): Int {
            var result = super.deviceId.hashCode() // The attachmentId is device-unique
            result = 31 * result + attachmentId.hashCode()
            return result
        }
    }

    /**
     * The metadata which describes the device which collected the data.
     *
     * @author Armin Schnabel
     * @property operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
     * @property deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     */
    data class DeviceMetaData(
        val operatingSystemVersion: String,
        val deviceType: String,
    ) : Serializable {
        init {
            require(
                operatingSystemVersion.isNotEmpty() &&
                        operatingSystemVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH
            ) {
                "Field osVersion had an invalid length of ${operatingSystemVersion.length.toLong()}"
            }
            require(deviceType.isNotEmpty() && deviceType.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
                "Field deviceType had an invalid length of ${deviceType.length.toLong()}"
            }
        }

        companion object {
            /**
             * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
             */
            @Suppress("ConstPropertyName")
            private const val serialVersionUID = 1L
        }
    }

    /**
     * The metadata which describes the application which collected the data.
     *
     * @author Armin Schnabel
     * @property applicationVersion The version of the app that transmitted the measurement.
     * @property formatVersion The format version of the upload file.
     */
    data class ApplicationMetaData(
        val applicationVersion: String,
        val formatVersion: Int,
    ) : Serializable {
        init {
            require(applicationVersion.isNotEmpty() && applicationVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
                "Field applicationVersion had an invalid length of ${applicationVersion.length.toLong()}"
            }
            if (formatVersion < CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
                throw DeprecatedFormatVersion("Deprecated formatVersion: ${formatVersion.toLong()}")
            } else if (formatVersion != CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
                throw UnknownFormatVersion("Unknown formatVersion: ${formatVersion.toLong()}")
            }
        }

        companion object {
            /**
             * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
             */
            @Suppress("ConstPropertyName")
            private const val serialVersionUID = 1L

            /**
             * The current version of the transferred file. This is always specified by the first two bytes of the file
             * transferred and helps compatible APIs to process data from different client versions.
             */
            const val CURRENT_TRANSFER_FILE_FORMAT_VERSION = 3
        }
    }

    /**
     * The metadata which describes the measurement the data was collected for.
     *
     * @author Armin Schnabel
     * @property length The length of the measurement in meters.
     * @property locationCount The count of geolocations in the transmitted measurement.
     * @property startLocation The first [GeoLocation] captured by the transmitted measurement.
     * @property endLocation The last [GeoLocation] captured by the transmitted measurement.
     * @property modality The modality type used to capture the measurement.
     */
    data class MeasurementMetaData(
        val length: Double,
        val locationCount: Long,
        val startLocation: GeoLocation?,
        val endLocation: GeoLocation?,
        val modality: String,
    ) : Serializable {
        init {
            if (locationCount < MINIMUM_LOCATION_COUNT) {
                throw TooFewLocations("LocationCount smaller than required: $locationCount")
            }
            requireNotNull(startLocation) {
                "Data incomplete startLocation was null!"
            }
            requireNotNull(endLocation) {
                "Data incomplete endLocation was null!"
            }
            require(length >= MINIMUM_TRACK_LENGTH) {
                "Field length had an invalid value smaller then 0.0: $length"
            }
            require(modality.isNotEmpty() && modality.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
                "Field modality had an invalid length of ${modality.length.toLong()}"
            }
        }

        /**
         * This class represents a geolocation at the start or end of a track.
         *
         * @author Armin Schnabel
         * @property timestamp The Unix timestamp this location was captured on in milliseconds.
         * @property latitude Geographical latitude (decimal fraction) raging from -90° (south) to 90° (north).
         * @property longitude Geographical longitude (decimal fraction) ranging from -180° (west) to 180° (east).
         */
        data class GeoLocation(
            val timestamp: Long,
            val latitude: Double,
            val longitude: Double
        ) {
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
            @Suppress("ConstPropertyName")
            private const val serialVersionUID = 1L

            /**
             * The minimum length of a track stored with a measurement.
             */
            private const val MINIMUM_TRACK_LENGTH = 0.0

            /**
             * The minimum valid amount of locations stored inside a measurement.
             */
            private const val MINIMUM_LOCATION_COUNT = 0L
        }
    }

    /**
     * The metadata which describes the attachments collected together with the measurement.
     *
     * @author Armin Schnabel
     * @property logCount Number of log files captured for this measurement, e.g. image capturing metrics.
     * @property imageCount Number of image files captured for this measurement.
     * @property videoCount Number of video files captured for this measurement.
     * @property filesSize The number of bytes of the files collected for this measurement (log, image and video data).
     */
    data class AttachmentMetaData(
        val logCount: Int,
        val imageCount: Int,
        val videoCount: Int,
        val filesSize: Long,
    ) : Serializable {
        init {
            require(logCount >= 0) { "Invalid logCount: $logCount" }
            require(imageCount >= 0) { "Invalid imageCount: $imageCount" }
            require(videoCount >= 0) { "Invalid videoCount: $videoCount" }
            require(filesSize >= 0) { "Invalid filesSize: $filesSize" }
        }

        companion object {
            /**
             * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
             */
            @Suppress("ConstPropertyName")
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 2L

        /**
         * Maximum size of a metadata field, with plenty space for future development. Prevents attackers from putting
         * arbitrary long data into these fields.
         */
        const val MAX_GENERIC_METADATA_FIELD_LENGTH = 30
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestMetaData<*>

        if (identifier != other.identifier) return false
        if (deviceMetaData != other.deviceMetaData) return false
        if (applicationMetaData != other.applicationMetaData) return false
        if (measurementMetaData != other.measurementMetaData) return false
        if (attachmentMetaData != other.attachmentMetaData) return false

        return true
    }

    override fun hashCode(): Int {
        return identifier.hashCode()
    }
}
