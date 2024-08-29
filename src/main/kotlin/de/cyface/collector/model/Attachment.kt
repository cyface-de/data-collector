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

import de.cyface.collector.handler.exception.AttachmentWithoutMeasurement
import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.handler.upload.PreRequestHandler
import de.cyface.collector.model.Uploadable.Companion.DEVICE_ID_FIELD
import de.cyface.collector.model.Uploadable.Companion.MEASUREMENT_ID_FIELD
import de.cyface.collector.model.metadata.ApplicationMetaData
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
 * Data which describes an uploadable attachment to a measurement.
 *
 * @author Klemens Muthmann
 * @property identifier The identifier of the attachment.
 * @property deviceMetaData The metadata of the device.
 * @property applicationMetaData The metadata of the application.
 * @property measurementMetaData The metadata of the measurement.
 * @property attachmentMetaData The metadata of the attachment.
 */
data class Attachment(
    val identifier: AttachmentIdentifier,
    private val deviceMetaData: DeviceMetaData,
    private val applicationMetaData: ApplicationMetaData,
    private val measurementMetaData: MeasurementMetaData,
    private val attachmentMetaData: AttachmentMetaData,
) : Uploadable {
    override fun bindTo(session: Session) {
        session.put(DEVICE_ID_FIELD, identifier.deviceIdentifier)
        session.put(MEASUREMENT_ID_FIELD, identifier.measurementIdentifier)
        session.put(ATTACHMENT_ID_FIELD, identifier.attachmentIdentifier)
    }

    override fun checkConflict(storage: DataStorageService): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        val deviceId = identifier.deviceIdentifier
        val measurementId = identifier.measurementIdentifier
        storage.isStored(deviceId.toString(), measurementId)
            .onSuccess { measurementExists ->
                if (!measurementExists) {
                    // Do not complete with false here, as this would just skip the attachment upload
                    promise.fail(AttachmentWithoutMeasurement("Measurement with id $measurementId does not exist"))
                } else {
                    storage.isStored(deviceId.toString(), measurementId, identifier.attachmentIdentifier)
                        .onSuccess { attachmentExists ->
                            // If the attachment already exists, return a conflict
                            promise.complete(attachmentExists)
                        }.onFailure { promise.fail(it) }
                }
            }.onFailure { promise.fail(it) }
        return promise.future()
    }

    override fun check(session: Session) {
        val measurementId = session.get<Any>(MEASUREMENT_ID_FIELD)
        val deviceId = session.get<Any>(DEVICE_ID_FIELD)
        val attachmentId = session.get<Any>(ATTACHMENT_ID_FIELD)
        if (measurementId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected measurement id: %s.", measurementId))
        }
        if (deviceId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected device id: %s.", deviceId))
        }
        if (attachmentId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected attachment id: %s.", attachmentId))
        }
    }

    override fun checkValidity(session: Session) {
        val sessionDeviceId = session.get<UUID>(DEVICE_ID_FIELD)
        val sessionMeasurementId = session.get<Long>(MEASUREMENT_ID_FIELD)
        val sessionAttachmentId = session.get<Long>(ATTACHMENT_ID_FIELD)
        if (sessionMeasurementId == null || sessionDeviceId == null || sessionAttachmentId == null) {
            throw SessionExpired("Did/mid/aid missing, session maybe expired, request upload restart (404).")
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
        if (sessionAttachmentId != identifier.attachmentIdentifier) {
            throw IllegalSession(
                String.format(
                    Locale.ENGLISH,
                    "Unexpected attachment id: %s.",
                    sessionAttachmentId
                )
            )
        }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.DEVICE_ID.value, identifier.deviceIdentifier.toString())
        ret.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementIdentifier.toString())
        ret.put(FormAttributes.ATTACHMENT_ID.value, identifier.attachmentIdentifier.toString())
        ret
            .mergeIn(deviceMetaData.toJson(), true)
            .mergeIn(applicationMetaData.toJson(), true)
            .mergeIn(measurementMetaData.toJson(), true)
            .mergeIn(attachmentMetaData.toJson(), true)
        return ret
    }

    override fun toGeoJson(): JsonObject {
        val geoJson = toGeoJson(deviceMetaData, applicationMetaData, measurementMetaData, attachmentMetaData)

        val properties = geoJson.getJsonObject("properties")
        properties.put(FormAttributes.DEVICE_ID.value, identifier.deviceIdentifier.toString())
        properties.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementIdentifier.toString())
        properties.put(FormAttributes.ATTACHMENT_ID.value, identifier.attachmentIdentifier.toString())

        return geoJson
    }

    companion object {
        /**
         * The field name for the session entry which contains the attachment id if this is an attachment upload.
         *
         * This field is set in the [PreRequestHandler] to ensure sessions are bound to attachments and
         * uploads are only accepted with an accepted pre request.
         */
        const val ATTACHMENT_ID_FIELD = "attachment-id"
    }
}

/**
 * World-unique identifier for an attachment captured by a specific device and for a specific measurement.
 *
 * @author Klemens Muthmann
 * @property deviceIdentifier The identifier of the device.
 * @property measurementIdentifier The identifier of the measurement.
 * @property attachmentIdentifier The identifier of the attachment.
 */
data class AttachmentIdentifier(
    val deviceIdentifier: UUID,
    val measurementIdentifier: Long,
    val attachmentIdentifier: Long
)

/**
 * Factory for creating [Attachment] instances from JSON objects.
 */
class AttachmentFactory : UploadableFactory {
    override fun from(json: JsonObject): Uploadable {
        try {
            // The metadata fields are stored as String (as they are also transmitted via header)
            // Thus, we need to read them as String first before converting them to the correct type.
            val deviceIdentifier = UUID.fromString(json.getString(FormAttributes.DEVICE_ID.value))
            val measurementIdentifier = json.getString(FormAttributes.MEASUREMENT_ID.value).toLong()
            val attachmentIdentifier = json.getString(FormAttributes.ATTACHMENT_ID.value).toLong()

            val applicationMetaData = ApplicationMetaData(json)
            val attachmentMetaData = AttachmentMetaData(json)
            val deviceMetaData = DeviceMetaData(json)
            val measurementMetaData = MeasurementMetaData(json)

            return Attachment(
                AttachmentIdentifier(deviceIdentifier, measurementIdentifier, attachmentIdentifier),
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

    override fun from(headers: MultiMap): Uploadable {
        try {
            val deviceId = UUID.fromString(requireNotNull(headers.get(FormAttributes.DEVICE_ID.value)))
            val measurementId = requireNotNull(headers.get(FormAttributes.MEASUREMENT_ID.value)).toLong()
            val attachmentIdentifier = requireNotNull(headers.get(FormAttributes.ATTACHMENT_ID.value)).toLong()

            val attachmentMetaData = AttachmentMetaData(headers)
            val applicationMetaData = ApplicationMetaData(headers)
            val measurementMetaData = MeasurementMetaData(headers)
            val deviceMetaData = DeviceMetaData(headers)

            return Attachment(
                AttachmentIdentifier(deviceId, measurementId, attachmentIdentifier),
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

    override fun attachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?
    ): AttachmentMetaData {
        if (logCount == null) throw InvalidMetaData("Data incomplete logCount was null!")
        if (imageCount == null) throw InvalidMetaData("Data incomplete imageCount was null!")
        if (videoCount == null) throw InvalidMetaData("Data incomplete videoCount was null!")
        if (filesSize == null) throw InvalidMetaData("Data incomplete filesSize was null!")
        if (logCount.toInt() == 0 && imageCount.toInt() == 0 && videoCount.toInt() == 0) {
            throw InvalidMetaData("No files registered for attachment.")
        }
        if (logCount.toInt() < 0 || imageCount.toInt() < 0 || videoCount.toInt() < 0) {
            throw InvalidMetaData("Invalid file count for attachment.")
        }
        if (filesSize.toLong() <= 0L) {
            throw InvalidMetaData("Files size for attachment must be greater than 0.")
        }
        return AttachmentMetaData(
            logCount.toInt(),
            imageCount.toInt(),
            videoCount.toInt(),
            filesSize.toLong(),
        )
    }
}
