/*
 * Copyright 2022-2024 Cyface GmbH
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
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.UploadMetaData
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import java.util.UUID

/**
 * Storage service to write data directly to the local file system.
 * This implementation should only be used for single node installations.
 * On clustered Vert.x repeating uploads is not going to work.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @property vertx The Vert.x instance used to access the data and the file system.
 */
@Suppress("unused")
class FileSystemStorageService(val vertx: Vertx) : DataStorageService {

    override fun store(
        sourceData: ReadStream<Buffer>,
        uploadMetaData: UploadMetaData
    ): Future<Status> {
        @Suppress("UnusedPrivateMember", "UNUSED_VARIABLE")
        val vertxFileSystem = vertx.fileSystem()
        @Suppress("ForbiddenComment")
        // TODO: What is the difference between createFile and writeFile?
        // TODO: How to append to an already existing file? Do I need to extend the interface with an append method?
        // pipe.to(measurement.binary.path)
        TODO("Not yet implemented")
    }

    override fun bytesUploaded(uploadIdentifier: UUID): Future<Long> {
        TODO("Not yet implemented")
    }

    override fun clean(uploadIdentifier: UUID): Future<Void> {
        TODO("Not yet implemented")
    }

    override fun startPeriodicCleaningOfTempData(
        uploadExpirationTime: Long,
        vertx: Vertx,
        cleanupOperation: CleanupOperation
    ) {
        TODO("Not yet implemented")
    }

    override fun isStored(deviceId: String, measurementId: Long): Future<Boolean> {
        TODO("Not yet implemented")
    }
}
