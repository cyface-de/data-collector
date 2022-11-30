package de.cyface.collector.storage.cloud

import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future

interface Database {
    fun storeMetadata(metaData: RequestMetaData): Future<String>
    fun exists(deviceIdentifier: String, measurementIdentifier: Long): Future<Boolean>
}
