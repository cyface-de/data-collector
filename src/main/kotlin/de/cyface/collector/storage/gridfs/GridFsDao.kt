package de.cyface.collector.storage.gridfs

import de.cyface.collector.model.Measurement
import de.cyface.collector.storage.exception.DuplicatesInDatabase
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.file.AsyncFile
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.GridFsUploadOptions
import io.vertx.ext.mongo.IndexOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.mongo.MongoGridFsClient
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.util.Locale

@Suppress("ForbiddenComment")
// TODO: This should probably also inherit from Database, but I did not find a way to unify the parameters.
open class GridFsDao(private val mongoClient: MongoClient) {

    private val logger = LoggerFactory.getLogger(GridFsDao::class.java)

    fun createIndices(): Future<CompositeFuture> {
        val ret = Promise.promise<CompositeFuture>()
        // Create indices
        val unique = IndexOptions().unique(true)
        val measurementIndex = JsonObject().put("metadata.deviceId", 1).put("metadata.measurementId", 1)
        // While the db stills contains `v2` data we allow 2 entries per did/mid: fileType:ccyfe & ccyf [DAT-1427]
        measurementIndex.put("metadata.fileType", 1)
        val measurementIndexCreation = mongoClient.createIndexWithOptions(
            "fs.files",
            measurementIndex,
            unique
        )
        val userIndex = JsonObject().put("username", 1)
        val userIndexCreation = mongoClient.createIndexWithOptions("user", userIndex, unique)

        CompositeFuture.all(measurementIndexCreation, userIndexCreation).onComplete {
            ret.complete(it.result())
        }
        return ret.future()
    }

    open fun store(measurement: Measurement, fileName: String, data: AsyncFile): Future<ObjectId> {
        val promise = Promise.promise<ObjectId>()
        val bucketServiceCreationCall = mongoClient.createDefaultGridFsBucketService()
        bucketServiceCreationCall.onFailure(promise::fail)
        bucketServiceCreationCall.onSuccess { gridfs ->
            val options = GridFsUploadOptions()
            options.metadata = measurement.toJson()
            val uploadCall = gridfs.uploadByFileNameWithOptions(data, fileName, options)
            uploadCall.onFailure(promise::fail)
            uploadCall.onSuccess { objectId -> promise.complete(ObjectId(objectId)) }
        }
        return promise.future()
    }

    fun exists(deviceId: String, measurementId: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()

        val access = mongoClient.createDefaultGridFsBucketService()
        access.onSuccess { gridFs: MongoGridFsClient ->
            try {
                val query = JsonObject()
                query.put("metadata.deviceId", deviceId)
                query.put("metadata.measurementId", measurementId.toString())
                val findIds = gridFs.findIds(query)
                findIds.onFailure(ret::fail)
                findIds.onSuccess { ids: List<String> ->
                    try {
                        if (ids.size > 1) {
                            logger.error("More than one measurement found for did {} mid {}", deviceId, measurementId)
                            ret.fail(
                                DuplicatesInDatabase(
                                    String.format(
                                        Locale.ENGLISH,
                                        "Found %d datasets with deviceId %s and measurementId %d",
                                        ids.size,
                                        deviceId,
                                        measurementId
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
            } catch (exception: RuntimeException) {
                ret.fail(exception)
            }
        }

        return ret.future()
    }
}
