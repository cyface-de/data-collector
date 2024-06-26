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

import de.cyface.collector.handler.AttachmentPreRequestHandler.Companion.ATTACHMENT_ID_FIELD
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.DEVICE_ID_FIELD
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.MEASUREMENT_ID_FIELD
import de.cyface.collector.handler.exception.AttachmentWithoutMeasurement
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.web.Session
import java.util.Locale

/**
 * Implementation of [RequestService] for attachment file uploads.
 *
 * @author Armin Schnabel
 */
class AttachmentRequestService(private val storageService: DataStorageService) : RequestService {
    override fun checkConflict(identifier: RequestMetaData.MeasurementIdentifier): Future<Boolean> {
        if (identifier !is RequestMetaData.AttachmentIdentifier) {
            throw Unparsable("Expected AttachmentIdentifier not passed for attachment upload.")
        }

        val promise = Promise.promise<Boolean>()
        val deviceId = identifier.deviceId
        val measurementId = identifier.measurementId
        storageService.isStored(deviceId, measurementId.toLong())
            .onSuccess { measurementExists ->
                if (!measurementExists) {
                    // Do not complete with false here, as this would just skip the attachment upload
                    promise.fail(AttachmentWithoutMeasurement("Measurement with id $measurementId does not exist"))
                } else {
                    storageService.isStored(deviceId, measurementId.toLong(), identifier.attachmentId.toLong())
                        .onSuccess { attachmentExists ->
                            // If the attachment already exists, return a conflict
                            promise.complete(attachmentExists)
                        }.onFailure { promise.fail(it) }
                }
            }.onFailure { promise.fail(it) }
        return promise.future()
    }

    override fun checkSession(session: Session) {
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

    override fun <T : RequestMetaData.MeasurementIdentifier> checkSessionValidity(
        session: Session,
        metaData: RequestMetaData<T>,
    ) {
        // Ensure this session was accepted by PreRequestHandler and bound to this measurement
        val sessionMeasurementId = session.get<String>(MEASUREMENT_ID_FIELD)
        val sessionDeviceId = session.get<String>(DEVICE_ID_FIELD)
        val sessionAttachmentId = session.get<String>(ATTACHMENT_ID_FIELD)
        if (sessionMeasurementId == null || sessionDeviceId == null || sessionAttachmentId == null) {
            throw SessionExpired("Did/mid/aid missing, session maybe expired, request upload restart (404).")
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
        val attachmentIdentifier = metaData.identifier as RequestMetaData.AttachmentIdentifier
        if (sessionAttachmentId.toLongOrNull() != attachmentIdentifier.attachmentId.toLong()) {
            throw IllegalSession(
                String.format(
                    Locale.ENGLISH,
                    "Unexpected attachment id: %s.",
                    sessionAttachmentId
                )
            )
        }
    }
}
