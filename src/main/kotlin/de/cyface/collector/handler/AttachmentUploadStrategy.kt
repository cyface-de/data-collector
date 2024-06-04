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

import de.cyface.collector.handler.exception.AttachmentWithoutMeasurement
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.Promise

/**
 * Strategy for checking conflicts with attachment uploads and the persistent storage.
 */
class AttachmentUploadStrategy(private val storageService: DataStorageService): UploadStrategy {

    override fun checkConflict(deviceId: String, measurementId: String, attachmentId: String?): Future<Boolean> {
        if (attachmentId?.toLongOrNull() == null) {
            throw InvalidMetaData("AttachmentId must be a valid number for attachment uploads")
        }

        val promise = Promise.promise<Boolean>()
        storageService.isStored(deviceId, measurementId.toLong())
            .onSuccess { measurementExists ->
                if (!measurementExists) {
                    // Do not complete with false here, as this would just skip the attachment upload
                    promise.fail(AttachmentWithoutMeasurement("Measurement with id $measurementId does not exist"))
                } else {
                    storageService.isStored(deviceId, measurementId.toLong(), attachmentId.toLong())
                        .onSuccess { attachmentExists ->
                            // If the attachment already exists, return a conflict
                            promise.complete(attachmentExists)
                        }.onFailure { promise.fail(it) }
                }
            }.onFailure { promise.fail(it) }
        return promise.future()
    }
}
