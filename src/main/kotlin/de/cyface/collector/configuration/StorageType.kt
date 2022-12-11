package de.cyface.collector.configuration

import de.cyface.collector.storage.DataStorageServiceBuilder
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient

interface StorageType {
    fun dataStorageServiceBuilder(vertx: Vertx, mongoClient: MongoClient): DataStorageServiceBuilder
}
