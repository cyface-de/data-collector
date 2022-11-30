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
package de.cyface.collector.storage.cloud

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import java.util.UUID
/**
 * A storage service to write data to a Google cloud store.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class GoogleCloudStorageService : DataStorageService {

    override fun store(
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        uploadIdentifier: UUID,
        metaData: RequestMetaData
    ): Future<Status> {
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