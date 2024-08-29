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
package de.cyface.collector.model

import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.model.Uploadable.Companion.DEVICE_ID_FIELD
import de.cyface.collector.model.Uploadable.Companion.MEASUREMENT_ID_FIELD
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.AttachmentCountsMissing
import de.cyface.collector.model.metadata.AttachmentMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Session
import java.util.Locale
import java.util.UUID

/**
 * Data which describes an uploadable measurement.
 *
 * @author Klemens Muthmann
 * @property identifier The identifier of the measurement.
 * @property deviceMetaData The metadata of the device.
 * @property applicationMetaData The metadata of the application.
 * @property measurementMetaData The metadata of the measurement.
 * @property attachmentMetaData The metadata of the attachments.
 */
data class Measurement(
    val identifier: MeasurementIdentifier,
    private val deviceMetaData: DeviceMetaData,
    private val applicationMetaData: ApplicationMetaData,
    private val measurementMetaData: MeasurementMetaData,
    private val attachmentMetaData: AttachmentMetaData,
) : Uploadable {
    override fun bindTo(session: Session) {
        session.put(DEVICE_ID_FIELD, identifier.deviceIdentifier)
        session.put(MEASUREMENT_ID_FIELD, identifier.measurementIdentifier)
    }

    override fun check(session: Session) {
        val measurementId = session.get<Any>(MEASUREMENT_ID_FIELD)
        val deviceId = session.get<Any>(DEVICE_ID_FIELD)
        if (measurementId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected measurement id: %s.", measurementId))
        }
        if (deviceId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected device id: %s.", deviceId))
        }
    }

    override fun checkConflict(storage: DataStorageService): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        val deviceId = identifier.deviceIdentifier
        val measurementId = identifier.measurementIdentifier
        storage.isStored(deviceId.toString(), measurementId)
            .onSuccess { measurementExists ->
                // If the measurement already exists, return a conflict
                promise.complete(measurementExists)
            }.onFailure { promise.fail(it) }
        return promise.future()
    }

    override fun checkValidity(session: Session) {
        // Ensure this session was accepted by PreRequestHandler and bound to this measurement
        val sessionDeviceId = session.get<UUID>(DEVICE_ID_FIELD)
        val sessionMeasurementId = session.get<Long>(MEASUREMENT_ID_FIELD)
        if (sessionMeasurementId == null || sessionDeviceId == null) {
            throw SessionExpired("Mid/did missing, session maybe expired, request upload restart (404).")
        }
        if (sessionMeasurementId != identifier.measurementIdentifier) {
            throw IllegalSession(
                String.format(
                    Locale.ENGLISH,
                    "Unexpected measurement id: %s.",
                    sessionMeasurementId
                )
            )
        }
        if (sessionDeviceId != identifier.deviceIdentifier) {
            throw IllegalSession(
                String.format(
                    Locale.ENGLISH,
                    "Unexpected device id: %s.",
                    sessionDeviceId
                )
            )
        }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.DEVICE_ID.value, identifier.deviceIdentifier.toString())
        ret.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementIdentifier.toString())
        ret
            .mergeIn(deviceMetaData.toJson(), true)
            .mergeIn(applicationMetaData.toJson(), true)
            .mergeIn(measurementMetaData.toJson(), true)
            .mergeIn(attachmentMetaData.toJson(), true)
        return ret
    }

    override fun toGeoJson(): JsonObject {
        val geoJson = toGeoJson(deviceMetaData, applicationMetaData, measurementMetaData, attachmentMetaData)

        geoJson
            .getJsonObject("properties")
            .put(FormAttributes.DEVICE_ID.value, identifier.deviceIdentifier.toString())
            .put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementIdentifier.toString())

        return geoJson
    }
}

/**
 * World-unique identifier for a measurement captured by a specific device.
 *
 * @author Klemens Muthmann
 * @property deviceIdentifier The identifier of the device.
 * @property measurementIdentifier The identifier of the measurement.
 */
data class MeasurementIdentifier(val deviceIdentifier: UUID, val measurementIdentifier: Long)

/**
 * Factory for creating measurements from JSON objects.
 */
class MeasurementFactory : UploadableFactory {
    override fun from(json: JsonObject): Uploadable {
        try {
            // The metadata fields are stored as String (as they are also transmitted via header)
            // Thus, we need to read them as String first before converting them to the correct type.
            val deviceIdentifier = UUID.fromString(json.getString(FormAttributes.DEVICE_ID.value))
            val measurementIdentifier = json.getString(FormAttributes.MEASUREMENT_ID.value).toLong()

            val applicationMetaData = ApplicationMetaData(json)
            val attachmentMetaData = try {
                AttachmentMetaData(json)
            } catch (@SuppressWarnings("SwallowedException") e: AttachmentCountsMissing) {
                // Ensures to be backward compatible with api V4 (measurements without attachment metadata)
                AttachmentMetaData(0, 0, 0, 0)
            }
            val deviceMetaData = DeviceMetaData(json)
            val measurementMetaData = MeasurementMetaData(json)

            return Measurement(
                MeasurementIdentifier(deviceIdentifier, measurementIdentifier),
                deviceMetaData,
                applicationMetaData,
                measurementMetaData,
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

    override fun attachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?
    ): AttachmentMetaData {
        // For backward compatibility we support measurement upload requests without attachment metadata
        val attachmentMetaMissing = logCount == null && imageCount == null && videoCount == null && filesSize == null
        if (attachmentMetaMissing) {
            return AttachmentMetaData(0, 0, 0, 0)
        } else {
            validateAttachmentMetaData(logCount, imageCount, videoCount, filesSize)
            return AttachmentMetaData(
                logCount!!.toInt(),
                imageCount!!.toInt(),
                videoCount!!.toInt(),
                filesSize!!.toLong(),
            )
        }
    }

    private fun validateAttachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?
    ) {
        if (logCount == null) throw InvalidMetaData("Data incomplete logCount was null!")
        if (imageCount == null) throw InvalidMetaData("Data incomplete imageCount was null!")
        if (videoCount == null) throw InvalidMetaData("Data incomplete videoCount was null!")
        if (filesSize == null) throw InvalidMetaData("Data incomplete filesSize was null!")
        if (logCount.toInt() < 0 || imageCount.toInt() < 0 || videoCount.toInt() < 0) {
            throw InvalidMetaData("Invalid file count for attachment.")
        }
        val attachmentCount = logCount.toInt() + imageCount.toInt() + videoCount.toInt()
        if (attachmentCount > 0 && filesSize.toLong() <= 0L) {
            throw InvalidMetaData("Files size for attachment must be greater than 0.")
        }
    }

    override fun from(headers: MultiMap): Uploadable {
        try {
            val deviceId = UUID.fromString(requireNotNull(headers.get(FormAttributes.DEVICE_ID.value)))
            val measurementId = requireNotNull(headers.get(FormAttributes.MEASUREMENT_ID.value)).toLong()

            val measurementIdentifier = MeasurementIdentifier(deviceId, measurementId)

            val attachmentMetaData = try {
                AttachmentMetaData(headers)
            } catch (@SuppressWarnings("SwallowedException") e: AttachmentCountsMissing) {
                // Ensures to be backward compatible with api V4 (measurements without attachment metadata)
                AttachmentMetaData(0, 0, 0, 0)
            }
            val applicationMetaData = ApplicationMetaData(headers)
            val measurementMetaData = MeasurementMetaData(headers)
            val deviceMetaData = DeviceMetaData(headers)

            return Measurement(
                measurementIdentifier,
                deviceMetaData,
                applicationMetaData,
                measurementMetaData,
                attachmentMetaData,
            )
        } catch (e: IllegalArgumentException) {
            throw InvalidMetaData("Data incomplete!", e)
        } catch (e: NumberFormatException) {
            throw InvalidMetaData("Data incomplete!", e)
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
}
