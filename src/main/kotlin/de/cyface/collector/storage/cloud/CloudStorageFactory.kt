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
package de.cyface.collector.storage.cloud

import java.util.UUID

/**
 * An abstraction for the creation of a `CloudStorage` implementation.
 * This is most useful to mock the instance for testing purposes, where no actual communication with the cloud is
 * desired.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 7.1.1
 */
fun interface CloudStorageFactory {
    /**
     * Build a new `CloudStorage` instance.
     */
    fun create(uploadIdentifier: UUID): CloudStorage
}
