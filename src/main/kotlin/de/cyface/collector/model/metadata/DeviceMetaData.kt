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
import de.cyface.collector.model.metadata.MetaData.Companion.MAX_GENERIC_METADATA_FIELD_LENGTH
import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject
import java.io.Serializable

/**
 * The metadata which describes the device which collected the data.
 *
 * @author Armin Schnabel
 * @property operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
 * @property deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
 */
data class DeviceMetaData(
    val operatingSystemVersion: String,
    val deviceType: String,
) : MetaData, Serializable {

    constructor(json: JsonObject) : this(
        json.getString(FormAttributes.OS_VERSION.value),
        json.getString(FormAttributes.DEVICE_TYPE.value)
    )

    constructor(headers: MultiMap) : this(
        headers.get(FormAttributes.OS_VERSION.value),
        headers.get(FormAttributes.DEVICE_TYPE.value)
    )

    init {
        require(
            operatingSystemVersion.isNotEmpty() &&
                operatingSystemVersion.length <= MAX_GENERIC_METADATA_FIELD_LENGTH
        ) {
            "Field osVersion had an invalid length of ${operatingSystemVersion.length.toLong()}"
        }
        require(deviceType.isNotEmpty() && deviceType.length <= MAX_GENERIC_METADATA_FIELD_LENGTH) {
            "Field deviceType had an invalid length of ${deviceType.length.toLong()}"
        }
    }

    override fun toJson(): JsonObject {
        val ret = JsonObject()
        ret.put(FormAttributes.OS_VERSION.value, operatingSystemVersion)
        ret.put(FormAttributes.DEVICE_TYPE.value, deviceType)
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
