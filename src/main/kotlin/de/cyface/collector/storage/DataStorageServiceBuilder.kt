package de.cyface.collector.storage

import io.vertx.core.Future

interface DataStorageServiceBuilder {
    fun create(): Future<DataStorageService>
    fun createCleanupOperation(): CleanupOperation
}
