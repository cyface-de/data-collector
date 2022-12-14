/*
 * Copyright 2022 Cyface GmbH
 *
 * This file is part of the Serialization.
 *
 * The Serialization is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Serialization is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Serialization. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.storage.cloud

import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future

/**
 * The interface to a database storing the metadata of some uploaded data.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface Database {
    /**
     * Stores the metadata asynchronously and returns a [Future], that is called on completion of that operation.
     */
    fun storeMetadata(metaData: RequestMetaData): Future<String>

    /**
     * Queries the database asynchronously for the existence of an upload with the provided `deviceIdentifier` and
     * `measurementIdentifier`.
     *
     * @return A [Future] that is called upon successful or failed completion of this operation.
     */
    fun exists(deviceIdentifier: String, measurementIdentifier: Long): Future<Boolean>
}
