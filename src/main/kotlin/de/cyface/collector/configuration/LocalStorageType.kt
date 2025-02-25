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
package de.cyface.collector.configuration

import de.cyface.collector.storage.DataStorageServiceBuilder
import de.cyface.collector.storage.local.FileSystemStorageServiceBuilder
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient

/**
 * The [StorageType] if local file storage is used.
 */
class LocalStorageType: StorageType {
    override fun dataStorageServiceBuilder(
        vertx: Vertx,
        mongoClient: MongoClient
    ): DataStorageServiceBuilder {
        return FileSystemStorageServiceBuilder(vertx)
    }
}