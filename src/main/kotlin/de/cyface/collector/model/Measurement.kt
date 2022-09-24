/*
 * Copyright 2018-2022 Cyface GmbH
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
package de.cyface.collector.model

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.json.JsonObject
import org.apache.commons.lang3.Validate
import java.io.File
import java.io.Serializable

/**
 * A POJO representing a single measurement, which has arrived at the API version 3 and needs to be stored to persistent
 * storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 7.0.0
 * @since 2.0.0
 * @property metaData The metadata from the request header.
 * @property userId The id of the user uploading the measurement.
 * @property binary The binary uploaded with the measurement. This contains the actual data.
 */
data class Measurement(val metaData: RequestMetaData, val userId: String, val binary: File) : Serializable {

    /**
     * @return A JSON representation of this measurement.
     */
    fun toJson(): JsonObject {
        val ret = metaData.toJson()
        // We can only store the usedId as string as:
        // - `new ObjectId(userId)` inserts `{timestamp:1654072354, date:1654072354000}` into the database
        // - `new JsonObject().put("$oid", userId))` leads to an exception: Invalid BSON field name $oid
        ret.put(USER_ID_FIELD, userId)
        return ret
    }

    /**
     * A `MessageCodec` implementation to be used for transmitting a `Measurement` via a Vertx
     * event bus.
     *
     * @author Klemens Muthmann
     * @author Armin Schnabel
     * @version 2.0.0
     * @since 1.0.0
     */
    internal class EventBusCodec : MessageCodec<Measurement, Measurement> {
        override fun encodeToWire(buffer: Buffer, serializable: Measurement) {
            val (deviceIdentifier, measurementIdentifier, operatingSystemVersion, deviceType, applicationVersion, length, locationCount, startLocation, endLocation, modality, formatVersion) = serializable.metaData
            val userId = serializable.userId
            buffer.appendInt(deviceIdentifier.length)
            buffer.appendInt(measurementIdentifier.length)
            buffer.appendInt(deviceType.length)
            buffer.appendInt(operatingSystemVersion.length)
            buffer.appendInt(applicationVersion.length)
            buffer.appendInt(modality.length)
            buffer.appendInt(userId.length)
            buffer.appendInt(formatVersion)
            buffer.appendString(deviceIdentifier)
            buffer.appendString(measurementIdentifier)
            buffer.appendString(deviceType)
            buffer.appendString(operatingSystemVersion)
            buffer.appendString(applicationVersion)
            buffer.appendDouble(length)
            buffer.appendLong(locationCount)
            buffer.appendString(modality)
            buffer.appendString(userId)
            if (locationCount > 0) {
                val startLocationLat = startLocation!!.latitude
                val startLocationLon = startLocation.longitude
                val startLocationTimestamp = startLocation.timestamp
                val endLocationLat = endLocation!!.latitude
                val endLocationLon = endLocation.longitude
                val endLocationTimestamp = endLocation.timestamp
                buffer.appendDouble(startLocationLat)
                buffer.appendDouble(startLocationLon)
                buffer.appendLong(startLocationTimestamp)
                buffer.appendDouble(endLocationLat)
                buffer.appendDouble(endLocationLon)
                buffer.appendLong(endLocationTimestamp)
            }

            // file upload (always one binary in version 3)
            val fu = serializable.binary
            buffer.appendInt(fu.absolutePath.length)
            buffer.appendString(fu.absolutePath)
        }

        override fun decodeFromWire(pos: Int, buffer: Buffer): Measurement {
            Validate.isTrue(pos == 0, String.format("Pos %d not supported", pos))
            val deviceIdentifierLength = buffer.getInt(0)
            val measurementIdentifierLength = buffer.getInt(Integer.BYTES)
            val deviceTypeLength = buffer.getInt(2 * Integer.BYTES)
            val operatingSystemVersionLength = buffer.getInt(3 * Integer.BYTES)
            val applicationVersionLength = buffer.getInt(4 * Integer.BYTES)
            val modalityLength = buffer.getInt(5 * Integer.BYTES)
            val usernameLength = buffer.getInt(6 * Integer.BYTES)
            val formatVersion = buffer.getInt(7 * Integer.BYTES)
            Validate.isTrue(formatVersion == 3)
            val deviceIdentifierEnd = 8 * Integer.BYTES + deviceIdentifierLength
            val deviceIdentifier = buffer.getString(8 * Integer.BYTES, deviceIdentifierEnd)
            val measurementIdentifierEnd = deviceIdentifierEnd + measurementIdentifierLength
            val measurementIdentifier = buffer.getString(deviceIdentifierEnd, measurementIdentifierEnd)
            val deviceTypeEnd = measurementIdentifierEnd + deviceTypeLength
            val deviceType = buffer.getString(measurementIdentifierEnd, deviceTypeEnd)
            val operationSystemVersionEnd = deviceTypeEnd + operatingSystemVersionLength
            val operatingSystemVersion = buffer.getString(deviceTypeEnd, operationSystemVersionEnd)
            val applicationVersionEnd = operationSystemVersionEnd + applicationVersionLength
            val applicationVersion = buffer.getString(operationSystemVersionEnd, applicationVersionEnd)
            val lengthEnd = applicationVersionEnd + java.lang.Double.BYTES
            val length = buffer.getDouble(applicationVersionEnd)
            val locationCountEnd = lengthEnd + java.lang.Long.BYTES
            val locationCount = buffer.getLong(lengthEnd)
            val modalityEnd = locationCountEnd + modalityLength
            val modality = buffer.getString(locationCountEnd, modalityEnd)
            val usernameEnd = modalityEnd + usernameLength
            val userId = buffer.getString(modalityEnd, usernameEnd)
            var startLocation: RequestMetaData.GeoLocation? = null
            var endLocation: RequestMetaData.GeoLocation? = null
            var startOfFileUploads = usernameEnd
            if (locationCount > 0) {
                val startLocationLatEnd = usernameEnd + java.lang.Double.BYTES
                val startLocationLonEnd = startLocationLatEnd + java.lang.Double.BYTES
                val startLocationTimestampEnd = startLocationLonEnd + java.lang.Long.BYTES
                val startLocationLat = buffer.getDouble(usernameEnd)
                val startLocationLon = buffer.getDouble(startLocationLatEnd)
                val startLocationTimestamp = buffer.getLong(startLocationLonEnd)
                val endLocationLatEnd = startLocationTimestampEnd + java.lang.Double.BYTES
                val endLocationLonEnd = endLocationLatEnd + java.lang.Double.BYTES
                val endLocationTimestampEnd = endLocationLonEnd + java.lang.Long.BYTES
                val endLocationLat = buffer.getDouble(startLocationTimestampEnd)
                val endLocationLon = buffer.getDouble(endLocationLatEnd)
                val endLocationTimestamp = buffer.getLong(endLocationLonEnd)
                startLocation = RequestMetaData.GeoLocation(
                    startLocationTimestamp, startLocationLat,
                    startLocationLon
                )
                endLocation = RequestMetaData.GeoLocation(endLocationTimestamp, endLocationLat, endLocationLon)
                startOfFileUploads = endLocationTimestampEnd
            }
            val entryLengthEnd = startOfFileUploads + Integer.BYTES
            val entryLength = buffer.getInt(startOfFileUploads)
            val fileNameEnd = entryLengthEnd + entryLength
            val fileName = buffer.getString(entryLengthEnd, fileNameEnd)
            val uploadFile = File(fileName)
            val metaData = RequestMetaData(
                deviceIdentifier, measurementIdentifier, operatingSystemVersion,
                deviceType, applicationVersion, length, locationCount, startLocation, endLocation, modality,
                formatVersion
            )
            return Measurement(metaData, userId, uploadFile)
        }

        override fun transform(serializable: Measurement): Measurement {
            return serializable
        }

        override fun name(): String {
            return "Measurement"
        }

        override fun systemCodecID(): Byte {
            return -1
        }
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        private const val serialVersionUID = -8304842300727933736L

        /**
         * The database field name which contains the user id of the measurement owner.
         */
        @JvmField
        var USER_ID_FIELD = "userId"

        /**
         * @return A codec encoding and decoding this `Measurement` for usage on the event bus.
         */
        @JvmStatic
        val codec: MessageCodec<Measurement, Measurement>
            get() = EventBusCodec()
    }
}