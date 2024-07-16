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
package de.cyface.collector.model.metadata

import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.model.FormAttributes
import de.cyface.collector.model.metadata.MetaData.Companion.MAX_GENERIC_METADATA_FIELD_LENGTH
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.Serializable

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
) : MetaData, Serializable {
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

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.LENGTH.value, length)
        ret.put(FormAttributes.LOCATION_COUNT.value, locationCount)
        if (startLocation != null) {
            ret.put("start", startLocation.geoJson())
        }
        if (endLocation != null) {
            ret.put("end", endLocation.geoJson())
        }
        ret.put(FormAttributes.MODALITY.value, modality)
        return ret
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
         * The minimum valid amount of locations stored inside a measurement, or else skip the upload.
         */
        private const val MINIMUM_LOCATION_COUNT = 2L
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

/**
 * Factory to create [GeoLocation] objects from given parameters.
 *
 * @author Armin Schnabel
 */
class GeoLocationFactory {
    /**
     * Creates a new [GeoLocation] object from the given parameters.
     *
     * @param timestamp The timestamp of the location.
     * @param latitude The latitude of the location.
     * @param longitude The longitude of the location.
     * @return The created geographical location object or `null` if any of the parameters is `null`.
     */
    fun from(timestamp: String?, latitude: String?, longitude: String?): GeoLocation? {
        return if (timestamp != null && latitude != null && longitude != null) {
            GeoLocation(
                timestamp.toLong(),
                latitude.toDouble(),
                longitude.toDouble(),
            )
        } else {
            null
        }
    }
}
