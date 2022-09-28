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

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import java.util.UUID

/**
 * Storage service to write data directly to the local file system.
 * This implementation should only be used for single node installations.
 * On clustered Vert.x repeating uploads is not going to work.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property vertx The Vert.x instance used to access the data and the file system.
 */
class FileSystemStorageService(val vertx: Vertx): DataStorageService {

    override fun store(
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        uploadIdentifier: UUID,
        metaData: RequestMetaData
    ): Future<Status> {
        val vertxFileSystem = vertx.fileSystem()
        // TODO: What is the difference between createFile and writeFile?
        // TODO: How to append to an already existing file? Do I need to extend the interface with an append method?
        //pipe.to(measurement.binary.path)
        TODO("Not yet implemented")
    }

    override fun bytesUploaded(uploadIdentifier: UUID): Future<Long> {
        TODO("Not yet implemented")
    }

    override fun clean(uploadIdentifier: UUID): Future<Void> {
        TODO("Not yet implemented")
    }

    override fun startPeriodicCleaningOfTempData(uploadExpirationTime: Long, vertx: Vertx) {
        TODO("Not yet implemented")
    }

    override fun isStored(deviceId: String, measurementId: Long): Future<Boolean> {
        TODO("Not yet implemented")
    }
}