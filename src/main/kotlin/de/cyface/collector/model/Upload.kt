/*
 * Copyright 2018-2024 Cyface GmbH
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

import io.vertx.core.json.JsonObject
import java.io.File
import java.io.Serializable

/**
 * A POJO representing a single measurement or attachment, which has arrived at the API version 3 and needs to be
 * stored to persistent storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @property uploadable The metadata from the request header.
 * @property userId The id of the user uploading the data.
 * @property binary The actual data uploaded.
 */
data class Upload (
    val uploadable: Uploadable,
    val userId: String,
    val binary: File,
) : Serializable {

    /**
     * @return A JSON representation of this measurement.
     */
    fun toJson(): JsonObject {
        val ret = uploadable.toJson()
        // We can only store the usedId as string as:
        // - `new ObjectId(userId)` inserts `{timestamp:1654072354, date:1654072354000}` into the database
        // - `new JsonObject().put("$oid", userId))` leads to an exception: Invalid BSON field name $oid
        ret.put(USER_ID_FIELD, userId)
        return ret
    }

    companion object {
        /**
         * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
         */
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = -8304842300727933736L

        /**
         * The database field name which contains the user id of the measurement owner.
         */
        @JvmField
        var USER_ID_FIELD = "userId"
    }
}
