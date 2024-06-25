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

import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject

/**
 * Implementation of [MetaDataService] for attachment file uploads.
 *
 * @author Armin Schnabel
 */
class AttachmentMetaDataService : MetaDataService {
    override fun identifier(metaData: JsonObject): RequestMetaData.AttachmentIdentifier {
        val deviceId = metaData.getString(FormAttributes.DEVICE_ID.value)
        val measurementId = metaData.getString(FormAttributes.MEASUREMENT_ID.value)
        val attachmentId = metaData.getString(FormAttributes.ATTACHMENT_ID.value)
        if (measurementId == null || deviceId == null || attachmentId == null) {
            throw InvalidMetaData("Data incomplete!")
        }
        return RequestMetaData.AttachmentIdentifier(deviceId, measurementId, attachmentId)
    }

    override fun identifier(headers: MultiMap): RequestMetaData.AttachmentIdentifier {
        val deviceId = headers.get(FormAttributes.DEVICE_ID.value)
        val measurementId = headers.get(FormAttributes.MEASUREMENT_ID.value)
        val attachmentId = headers.get(FormAttributes.ATTACHMENT_ID.value)
        if (deviceId == null || measurementId == null || attachmentId == null) {
            throw InvalidMetaData("Data incomplete!")
        }
        return RequestMetaData.AttachmentIdentifier(deviceId, measurementId, attachmentId)
    }

    override fun attachmentMetaData(
        logCount: String?,
        imageCount: String?,
        videoCount: String?,
        filesSize: String?
    ): RequestMetaData.AttachmentMetaData {
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
