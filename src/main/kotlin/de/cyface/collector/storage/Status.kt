/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.collector.storage

import java.util.UUID

/**
 * The return status of storing some data to an external storage system.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property uploadIdentifier The identifier used to identify an upload between several requests.
 * @property type The type of the status, as provided by the enumeration ``StatusType``.
 * @property byteSize The number of bytes uploaded via the operation, that returned the status.
 */
data class Status(val uploadIdentifier: UUID, val type: StatusType, val byteSize: Long)

/**
 * The type of an upload `Status`, like if it was completed or not.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
enum class StatusType {
    /**
     * Returned in case the upload finished upload of a file.
     */
    COMPLETE,

    /**
     * Returned if the upload finished but more of this file is expected.
     */
    INCOMPLETE
}