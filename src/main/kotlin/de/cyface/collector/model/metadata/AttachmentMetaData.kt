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
package de.cyface.collector.model.metadata

import de.cyface.collector.model.FormAttributes
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject
import java.io.Serializable

/**
 * The metadata which describes the attachments collected together with the measurement.
 *
 * @author Armin Schnabel
 * @property logCount Number of log files captured for this measurement, e.g. image capturing metrics.
 * @property imageCount Number of image files captured for this measurement.
 * @property videoCount Number of video files captured for this measurement.
 * @property filesSize The number of bytes of the files collected for this measurement (log, image and video data).
 */
data class AttachmentMetaData(
    val logCount: Int,
    val imageCount: Int,
    val videoCount: Int,
    val filesSize: Long,
) : MetaData, Serializable {

    /**
     * Extracts the attachment specific metadata from the request body.
     *
     * @param body The request body containing the metadata.
     */
    constructor(body: JsonObject) : this(
        // The metadata fields are stored as String (as they are also transmitted via header)
        // Thus, we need to read them as String first before converting them to the correct type.
        body.getString(FormAttributes.LOG_COUNT.value)?.toInt() ?: throw AttachmentCountsMissing(),
        body.getString(FormAttributes.IMAGE_COUNT.value)?.toInt() ?: throw AttachmentCountsMissing(),
        body.getString(FormAttributes.VIDEO_COUNT.value)?.toInt() ?: throw AttachmentCountsMissing(),
        body.getString(FormAttributes.FILES_SIZE.value)?.toLong() ?: throw AttachmentCountsMissing(),
    )

    /**
     * Extracts the attachment specific metadata from the request headers.
     *
     * @param headers The request headers containing the metadata.
     */
    constructor(headers: MultiMap) : this(
        headers.get(FormAttributes.LOG_COUNT.value)?.toInt() ?: throw AttachmentCountsMissing(),
        headers.get(FormAttributes.IMAGE_COUNT.value)?.toInt() ?: throw AttachmentCountsMissing(),
        headers.get(FormAttributes.VIDEO_COUNT.value)?.toInt() ?: throw AttachmentCountsMissing(),
        headers.get(FormAttributes.FILES_SIZE.value)?.toLong() ?: throw AttachmentCountsMissing(),
    )

    init {
        require(logCount >= 0) { "Invalid logCount: $logCount" }
        require(imageCount >= 0) { "Invalid imageCount: $imageCount" }
        require(videoCount >= 0) { "Invalid videoCount: $videoCount" }
        require(filesSize >= 0) { "Invalid filesSize: $filesSize" }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.LOG_COUNT.value, logCount)
        ret.put(FormAttributes.IMAGE_COUNT.value, imageCount)
        ret.put(FormAttributes.VIDEO_COUNT.value, videoCount)
        ret.put(FormAttributes.FILES_SIZE.value, filesSize)
        return ret
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
