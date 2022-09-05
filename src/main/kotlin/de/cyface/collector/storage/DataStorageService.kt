package de.cyface.collector.storage

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import java.util.UUID

sealed interface DataStorageService {
    fun store(pipe: Pipe<Buffer>, user: User, contentRange: ContentRange, uploadIdentifier: UUID, metaData: RequestMetaData): Future<Status>
    fun isStored(measurement: Measurement): Future<Boolean>
    fun bytesUploaded(uploadIdentifier: UUID): Future<Long>
    fun clean(uploadIdentifier: UUID): Future<Void>
    fun startPeriodicCleaningOfTempData(uploadExpirationTime: Long, vertx: Vertx)
}