package de.cyface.collector.storage.cloud

import de.cyface.collector.handler.FormAttributes
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.exception.DuplicatesInDatabase
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import org.slf4j.LoggerFactory
import java.util.Locale

class MongoDatabase(private val mongoClient: MongoClient, private val collectionName: String) : Database {

    private val logger = LoggerFactory.getLogger(MongoDatabase::class.java)

    override fun storeMetadata(metaData: RequestMetaData): Future<String> {
        return mongoClient.insert(collectionName, metaData.toGeoJson())
    }

    override fun exists(deviceIdentifier: String, measurementIdentifier: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()
        val query = JsonObject()
        query.put("features.0.properties.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
        query.put("features.0.properties.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier)

        val queryCall = mongoClient.find(collectionName, query)
        queryCall.onSuccess { ids ->
            try {
                if (ids.size > 1) {
                    logger.error(
                        "More than one measurement found for did {} mid {}",
                        deviceIdentifier,
                        measurementIdentifier
                    )
                    ret.fail(
                        DuplicatesInDatabase(
                            String.format(
                                Locale.ENGLISH,
                                "Found %d datasets with deviceId %s and measurementId %d",
                                ids.size,
                                deviceIdentifier,
                                measurementIdentifier
                            )
                        )
                    )
                } else if (ids.size == 1) {
                    ret.complete(true)
                } else {
                    ret.complete(false)
                }
            } catch (exception: RuntimeException) {
                ret.fail(exception)
            }
        }
        queryCall.onFailure(ret::fail)

        return ret.future()
    }
}
