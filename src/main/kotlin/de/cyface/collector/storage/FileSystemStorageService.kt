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

    override fun isStored(measurement: Measurement): Future<Boolean> {
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
}