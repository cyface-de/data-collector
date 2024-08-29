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

import de.cyface.collector.handler.exception.DeprecatedFormatVersion
import de.cyface.collector.handler.exception.UnknownFormatVersion
import de.cyface.collector.model.FormAttributes
import de.cyface.collector.model.metadata.MetaData.Companion.MAX_GENERIC_METADATA_FIELD_LENGTH
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject
import java.io.Serializable

/**
 * The metadata which describes the application which collected the data.
 *
 * @author Armin Schnabel
 * @property applicationVersion The version of the app that transmitted the measurement.
 * @property formatVersion The format version of the upload file.
 */
data class ApplicationMetaData(
    val applicationVersion: String,
    val formatVersion: Int,
) : MetaData, Serializable {

    /**
     * Extracts the device specific metadata from the request body.
     *
     * @param json The request body containing the metadata.
     * @return The extracted metadata.
     */
    constructor(json: JsonObject) : this(
        // The metadata fields are stored as String (as they are also transmitted via header)
        // Thus, we need to read them as String first before converting them to the correct type.
        json.getString(FormAttributes.APPLICATION_VERSION.value),
        json.getString(FormAttributes.FORMAT_VERSION.value).toInt()
    )

    /**
     * Extracts the application specific metadata from the request headers.
     *
     * @param headers The request headers containing the metadata.
     * @return The extracted metadata.
     */
    constructor(headers: MultiMap) : this(
        headers.get(FormAttributes.APPLICATION_VERSION.value),
        headers.get(FormAttributes.FORMAT_VERSION.value).toInt()
    )

    init {
        require(applicationVersion.isNotEmpty() && applicationVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
            "Field applicationVersion had an invalid length of ${applicationVersion.length.toLong()}"
        }
        if (formatVersion < CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
            throw DeprecatedFormatVersion("Deprecated formatVersion: ${formatVersion.toLong()}")
        } else if (formatVersion != CURRENT_TRANSFER_FILE_FORMAT_VERSION) {
            throw UnknownFormatVersion("Unknown formatVersion: ${formatVersion.toLong()}")
        }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.APPLICATION_VERSION.value, applicationVersion)
        ret.put(FormAttributes.FORMAT_VERSION.value, formatVersion)
        return ret
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L

        /**
         * The current version of the transferred file. This is always specified by the first two bytes of the file
         * transferred and helps compatible APIs to process data from different client versions.
         */
        const val CURRENT_TRANSFER_FILE_FORMAT_VERSION = 3
    }
}
