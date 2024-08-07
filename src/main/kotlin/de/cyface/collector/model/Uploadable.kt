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

import de.cyface.collector.handler.upload.PreRequestHandler
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.AttachmentMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Session

/**
 * Interface for object types which the Collector accepts as upload.
 *
 * @author Klemens Muthmann
 */
interface Uploadable {
    /**
     * Binds the current session to the uploadable.
     *
     * @param session The session to bind to the uploadable.
     */
    fun bindTo(session: Session)

    /**
     * Checks that the current session was accepted by PreRequestHandler and bound to this uploadable.
     *
     * @param session The session to check.
     */
    fun check(session: Session)

    /**
     * Checks if the uploadable does not conflict with existing data in the storage.
     *
     * @param storage The storage to check for conflicts.
     * @return A future which resolves to true if no conflict was found, false otherwise.
     */
    fun checkConflict(storage: DataStorageService): Future<Boolean>

    /**
     * Checks if the uploadable matches the uploadable the session was bound to.
     *
     * @param session The session to check.
     * @return True if the uploadable matches the session, false otherwise.
     */
    fun checkValidity(session: Session)

    /**
     * Transform this object into a generic JSON representation.
     *
     * @return The JSON representation of this object.
     */
    fun toJson(): JsonObject

    /**
     * Transform this object into a GeoJSON Feature.
     *
     * @return The GeoJSON representation of this object.
     */
    fun toGeoJson(): JsonObject

    /**
     * Transform this object into a GeoJSON FeatureCollection.
     *
     * @param deviceMetaData The metadata of the device.
     * @param applicationMetaData The metadata of the application.
     * @param measurementMetaData The metadata of the measurement.
     * @param attachmentMetaData The metadata of the attachments.
     * @return The GeoJSON representation of this object.
     */
    fun toGeoJson(
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData,
        measurementMetaData: MeasurementMetaData,
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

/**
 * Factory interface for creating uploadable objects.
 *
 * @author Klemens Muthmann
 */
interface UploadableFactory {
    /**
     * Creates an uploadable object from the metadata body.
     *
     * @param json The metadata body sent in the pre-request.
     * @return The created uploadable object.
     */
    fun from(json: JsonObject): Uploadable

    /**
     * Creates an uploadable object from the metadata header.
     *
     * @param headers The metadata headers sent in the pre-request.
     * @return The created uploadable object.
     */
    fun from(headers: MultiMap): Uploadable

    /**
     * Creates an attachment metadata object from the given values.
     *
     * @param logCount The number of log files captured for this measurement.
     * @param imageCount The number of image files captured for this measurement.
     * @param videoCount The number of video files captured for this measurement.
     * @param filesSize The number of bytes of the attachment files.
     * @return The created attachment metadata object.
     */
    fun attachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?,
    ): AttachmentMetaData
}
