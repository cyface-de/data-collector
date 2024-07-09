package de.cyface.collector.model

import de.cyface.collector.handler.FormAttributes
import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.TooFewLocations
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.model.Attachment.Companion.ATTACHMENT_ID_FIELD
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

data class Measurement(
    val identifier: MeasurementIdentifier,
    private val applicationMetaData: ApplicationMetaData,
    private val attachmentMetaData: AttachmentMetaData,
    private val deviceMetaData: DeviceMetaData,
    private val measurementMetaData: MeasurementMetaData
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
        val sessionMeasurementId = session.get<Long>(MEASUREMENT_ID_FIELD)
        val sessionDeviceId = session.get<String>(DEVICE_ID_FIELD)
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
        if (UUID.fromString(sessionDeviceId) != identifier.deviceIdentifier) {
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
        ret.put(FormAttributes.DEVICE_ID.value, identifier.deviceIdentifier)
        ret.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementIdentifier)
        ret
            .mergeIn(applicationMetaData.toJson(), true)
            .mergeIn(attachmentMetaData.toJson(), true)
            .mergeIn(deviceMetaData.toJson(), true)
            .mergeIn(measurementMetaData.toJson(), true)
        return ret
    }

    override fun toGeoJson(): JsonObject {
        val geoJson = toGeoJson(measurementMetaData, deviceMetaData, applicationMetaData, attachmentMetaData)

        val properties = geoJson.getJsonObject("properties")
        properties.put(FormAttributes.DEVICE_ID.value, identifier.deviceIdentifier)
        properties.put(FormAttributes.MEASUREMENT_ID.value, identifier.measurementIdentifier)

        return geoJson
    }

}

data class MeasurementIdentifier(val deviceIdentifier: UUID, val measurementIdentifier: Long)

class MeasurementFactory : UploadableFactory {
    override fun from(json: JsonObject): Uploadable {
        try {
            val deviceIdentifier = UUID.fromString(json.getString(FormAttributes.DEVICE_ID.value))
            val measurementIdentifier = json.getLong(FormAttributes.MEASUREMENT_ID.value)

            val applicationMetaData = applicationMetaData(json)
            val attachmentMetaData = attachmentMetaData(json)
            val deviceMetaData = deviceMetaData(json)
            val measurementMetaData = measurementMetaData(json)

            return Measurement(
                MeasurementIdentifier(deviceIdentifier, measurementIdentifier),
                applicationMetaData,
                attachmentMetaData,
                deviceMetaData,
                measurementMetaData,
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
        if (logCount.toInt() == 0 && imageCount.toInt() == 0 && videoCount.toInt() == 0) {
            throw InvalidMetaData("No files registered for attachment.")
        }
        if (logCount.toInt() < 0 || imageCount.toInt() < 0 || videoCount.toInt() < 0) {
            throw InvalidMetaData("Invalid file count for attachment.")
        }
        if (filesSize.toLong() <= 0L) {
            throw InvalidMetaData("Files size for attachment must be greater than 0.")
        }
    }

    override fun from(headers: MultiMap): Uploadable {
        try {
            val deviceId = UUID.fromString(requireNotNull(headers.get(FormAttributes.DEVICE_ID.value)))
            val measurementId = requireNotNull(headers.get(FormAttributes.MEASUREMENT_ID.value)).toLong()

            val measurementIdentifier = MeasurementIdentifier(deviceId, measurementId)

            val attachmentMetaData = attachmentMetaData(headers)
            val applicationMetaData = applicationMetaData(headers)
            val measurementMetaData = measurementMetaData(headers)
            val deviceMetaData = deviceMetaData(headers)

            return Measurement(
                measurementIdentifier,
                applicationMetaData,
                attachmentMetaData,
                deviceMetaData,
                measurementMetaData
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
