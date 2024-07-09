package de.cyface.collector.model

import de.cyface.collector.handler.FormAttributes
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.handler.upload.PreRequestHandler
import de.cyface.collector.model.metadata.AttachmentMetaData
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Session

interface Uploadable {
    fun bindTo(session: Session)
    fun check(session: Session)
    fun checkConflict(storage: DataStorageService): Future<Boolean>
    fun checkValidity(session: Session)
    /**
     * Transform this object into a generic JSON representation.
     */
    fun toJson(): JsonObject
    fun toGeoJson(): JsonObject

    fun toGeoJson(
        measurementMetaData: MeasurementMetaData,
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData,
        attachmentMetaData: AttachmentMetaData,
    ): JsonObject {
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


    companion object {
        /**
         * The field name for the session entry which contains the measurement id.
         *
         * This field is set in the [PreRequestHandler] to ensure sessions are bound to measurements and
         * uploads are only accepted with an accepted pre request.
         */
        const val MEASUREMENT_ID_FIELD = "measurement-id"

        /**
         * The field name for the session entry which contains the device id.
         *
         * This field is set in the [PreRequestHandler] to ensure sessions are bound to measurements and
         * uploads are only accepted with an accepted pre request.
         */
        const val DEVICE_ID_FIELD = "device-id"
    }
}

interface UploadableFactory {
    fun from(json: JsonObject): Uploadable
    fun from(headers: MultiMap): Uploadable
    fun attachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?,
    ): AttachmentMetaData

    /**
     * Extracts the attachment specific metadata from the request body.
     *
     * @param body The request body containing the metadata.
     * @return The extracted metadata.
     */
    fun attachmentMetaData(body: JsonObject): AttachmentMetaData {
        val logCount = body.getString(FormAttributes.LOG_COUNT.value)
        val imageCount = body.getString(FormAttributes.IMAGE_COUNT.value)
        val videoCount = body.getString(FormAttributes.VIDEO_COUNT.value)
        val filesSize = body.getString(FormAttributes.FILES_SIZE.value)
        return attachmentMetaData(logCount, imageCount, videoCount, filesSize)
    }

    fun attachmentMetaData(headers: MultiMap): AttachmentMetaData {
        val logCount = headers.get(FormAttributes.LOG_COUNT.value)
        val imageCount = headers.get(FormAttributes.IMAGE_COUNT.value)
        val videoCount = headers.get(FormAttributes.VIDEO_COUNT.value)
        val filesSize = headers.get(FormAttributes.FILES_SIZE.value)
        return attachmentMetaData(logCount, imageCount, videoCount, filesSize)
    }

    fun deviceMetaData(json: JsonObject): DeviceMetaData {
        val osVersion = json.getString(FormAttributes.OS_VERSION.value)
        val deviceType = json.getString(FormAttributes.DEVICE_TYPE.value)
        return DeviceMetaData(osVersion, deviceType)
    }

    fun applicationMetaData(json: JsonObject): ApplicationMetaData {
        val appVersion = json.getString(FormAttributes.APPLICATION_VERSION.value)
        val formatVersion = json.getInteger(FormAttributes.FORMAT_VERSION.value)
        return ApplicationMetaData(appVersion, formatVersion)
    }

    fun measurementMetaData(json: JsonObject): MeasurementMetaData {
        val length = json.getString(FormAttributes.LENGTH.value).toDouble()
        val locationCount = json.getString(FormAttributes.LOCATION_COUNT.value).toLong()
        val startLocationLat = json.getString(FormAttributes.START_LOCATION_LAT.value)
        val startLocationLon = json.getString(FormAttributes.START_LOCATION_LON.value)
        val startLocationTs = json.getString(FormAttributes.START_LOCATION_TS.value)
        val endLocationLat = json.getString(FormAttributes.END_LOCATION_LAT.value)
        val endLocationLon = json.getString(FormAttributes.END_LOCATION_LON.value)
        val endLocationTs = json.getString(FormAttributes.END_LOCATION_TS.value)
        val startLocation = geoLocation(startLocationTs, startLocationLat, startLocationLon)
        val endLocation = geoLocation(endLocationTs, endLocationLat, endLocationLon)
        val modality = json.getString(FormAttributes.MODALITY.value)
        return MeasurementMetaData(length, locationCount, startLocation, endLocation, modality)
    }

    private fun geoLocation(timestamp: String?, latitude: String?, longitude: String?):
            GeoLocation? {
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

    fun deviceMetaData(headers: MultiMap): DeviceMetaData {
        val osVersion = headers.get(FormAttributes.OS_VERSION.value)
        val deviceType = headers.get(FormAttributes.DEVICE_TYPE.value)
        return DeviceMetaData(osVersion, deviceType)
    }

    fun applicationMetaData(headers: MultiMap): ApplicationMetaData {
        val appVersion = headers.get(FormAttributes.APPLICATION_VERSION.value)
        val formatVersion = headers.get(FormAttributes.FORMAT_VERSION.value).toInt()

        return ApplicationMetaData(appVersion, formatVersion)
    }

    fun measurementMetaData(headers: MultiMap): MeasurementMetaData {
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

        return MeasurementMetaData(
            length,
            locationCount,
            startLocation,
            endLocation,
            modality
        )
    }
}
