package de.cyface.collector.storage

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe

class GoogleCloudStorageService: DataStorageService {
    override fun store(pipe: Pipe<Buffer>, user: User, contentRange: ContentRange, metaData: RequestMetaData, path: String?): Future<Status> {
        TODO("Not yet implemented")
    }

    override fun isStored(measurement: Measurement): Future<Boolean> {
        TODO("Not yet implemented")
    }

    override fun bytesUploaded(measurement: Measurement): Future<Long> {
        TODO("Not yet implemented")
    }

}