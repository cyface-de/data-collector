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

import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.DEVICE_ID_FIELD
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.MEASUREMENT_ID_FIELD
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Session
import java.util.Locale

/**
 * Implementation of [CheckService] for measurement file uploads.
 *
 * @author Armin Schnabel
 */
class MeasurementCheckService(private val storageService: DataStorageService) : CheckService {
    override fun identifier(metaData: JsonObject): RequestMetaData.MeasurementIdentifier {
        val deviceId = metaData.getString(FormAttributes.DEVICE_ID.value)
        val measurementId = metaData.getString(FormAttributes.MEASUREMENT_ID.value)
        val attachmentId = metaData.getString(FormAttributes.ATTACHMENT_ID.value)
        if (attachmentId != null) {
            throw InvalidMetaData("Unexpected attachmentId found!")
        }
        return RequestMetaData.MeasurementIdentifier(deviceId, measurementId)
    }

    override fun identifier(headers: MultiMap): RequestMetaData.MeasurementIdentifier {
        val deviceId = headers.get(FormAttributes.DEVICE_ID.value)
        val measurementId = headers.get(FormAttributes.MEASUREMENT_ID.value)
        if (deviceId == null || measurementId == null) {
            throw InvalidMetaData("Data incomplete!")
        }
        return RequestMetaData.MeasurementIdentifier(deviceId, measurementId)
    }

    override fun attachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?
    ): RequestMetaData.AttachmentMetaData {
        // For backward compatibility we support measurement upload requests without attachment metadata
        if (logCount == null && imageCount == null && videoCount == null && filesSize == null) {
            return RequestMetaData.AttachmentMetaData(0, 0, 0, 0)
        } else {
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
            return RequestMetaData.AttachmentMetaData(
                logCount.toInt(),
                imageCount.toInt(),
                videoCount.toInt(),
                filesSize.toLong(),
            )
        }
    }

    override fun checkConflict(identifier: RequestMetaData.MeasurementIdentifier): Future<Boolean> {
        if (identifier is RequestMetaData.AttachmentIdentifier) {
            throw Unparsable("Unexpected AttachmentIdentifier passed for measurement upload.")
        }

        val promise = Promise.promise<Boolean>()
        val deviceId = identifier.deviceId
        val measurementId = identifier.measurementId
        storageService.isStored(deviceId, measurementId.toLong())
            .onSuccess { measurementExists ->
                // If the measurement already exists, return a conflict
                promise.complete(measurementExists)
            }.onFailure { promise.fail(it) }
        return promise.future()
    }

    override fun checkSession(session: Session) {
        val measurementId = session.get<Any>(MEASUREMENT_ID_FIELD)
        val deviceId = session.get<Any>(DEVICE_ID_FIELD)
        if (measurementId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected measurement id: %s.", measurementId))
        }
        if (deviceId != null) {
            throw IllegalSession(String.format(Locale.ENGLISH, "Unexpected device id: %s.", deviceId))
        }
    }

    override fun <T : RequestMetaData.MeasurementIdentifier> checkSessionValidity(
        session: Session,
        metaData: RequestMetaData<T>,
    ) {
        // Ensure this session was accepted by PreRequestHandler and bound to this measurement
        val sessionMeasurementId = session.get<String>(MEASUREMENT_ID_FIELD)
        val sessionDeviceId = session.get<String>(DEVICE_ID_FIELD)
        if (sessionMeasurementId == null || sessionDeviceId == null) {
            throw SessionExpired("Mid/did missing, session maybe expired, request upload restart (404).")
        }
        if (sessionMeasurementId != metaData.identifier.measurementId) {
            throw IllegalSession(
                String.format(
                    Locale.ENGLISH,
                    "Unexpected measurement id: %s.",
                    sessionMeasurementId
                )
            )
        }
        if (sessionDeviceId != metaData.identifier.deviceId) {
            throw IllegalSession(
                String.format(
                    Locale.ENGLISH,
                    "Unexpected device id: %s.",
                    sessionDeviceId
                )
            )
        }
    }
}
