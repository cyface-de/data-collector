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
package de.cyface.collector.configuration

import de.cyface.collector.storage.DataStorageServiceBuilder
import de.cyface.collector.storage.gridfs.GridFsDao
import de.cyface.collector.storage.gridfs.GridFsStorageServiceBuilder
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient
import org.apache.commons.lang3.Validate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Configuration for a [de.cyface.collector.storage.DataStorageService] using Mongo GridFS to store data.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property uploadFolder The [Path] to the folder to upload data to.
 */
data class GridFsStorageType(val uploadFolder: Path) : StorageType {
    override fun dataStorageServiceBuilder(vertx: Vertx, mongoClient: MongoClient): DataStorageServiceBuilder {
        if (!uploadFolder.exists()) {
            val createdDirectory = Files.createDirectory(uploadFolder)
            Validate.isTrue(createdDirectory.exists())
        }
        val fileSystem = vertx.fileSystem()
        val dao = GridFsDao(mongoClient)
        return GridFsStorageServiceBuilder(dao, fileSystem, uploadFolder)
    }
}
