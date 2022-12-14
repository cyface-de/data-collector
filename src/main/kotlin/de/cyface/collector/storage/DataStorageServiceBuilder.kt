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
package de.cyface.collector.storage

import io.vertx.core.Future

/**
 * Instances of this class are responsible for creating specific [DataStorageService] implementations.
 *
 * This builder is required for two reasons:
 * 1. To ensure that the created `DataStorageService` matches the [CleanupOperation].
 * 2. To postpone the often costly creation of a `DataStorageService` to a convenient point in time.
 *
 * The builder provides the opportunity to collect all necessary information for the creation of a `DataStorageService`
 * and inject it into objects, that do the actual creation, without them knowing which service they are going to use.
 * That way the `DataStorageService` ensures loose coupling between `DataStorageService` instances and the objects
 * requiring such a service.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface DataStorageServiceBuilder {
    /**
     * Start the creation process of a [de.cyface.collector.storage.DataStorageService] and provide a [Future], that
     * will be notified about successful or failed completion.
     */
    fun create(): Future<DataStorageService>

    /**
     * Create a [CleanupOperation] matching the [DataStorageService] created by this builder.
     */
    fun createCleanupOperation(): CleanupOperation
}
