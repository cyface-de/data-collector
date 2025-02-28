/*
 * Copyright 2025 Cyface GmbH
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
package de.cyface.collector.storage.local

import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.DataStorageServiceBuilder
import io.vertx.core.Future
import io.vertx.core.Vertx

/**
 * A builder for the [FileSystemStorageService].
 *
 * @author Klemens Muthmann
 */
class FileSystemStorageServiceBuilder(private val vertx: Vertx) : DataStorageServiceBuilder {
    /**
     * Build the actual service.
     */
    private fun build(): FileSystemStorageService {
        return FileSystemStorageService(vertx)
    }

    override fun create(): Future<DataStorageService> {
        return Future.succeededFuture(build())
    }

    override fun createCleanupOperation(): CleanupOperation {
        return object : CleanupOperation {
            override fun clean(fileExpirationTime: Long) {
                // Nothing to do here at the moment.
            }
        }
    }
}
