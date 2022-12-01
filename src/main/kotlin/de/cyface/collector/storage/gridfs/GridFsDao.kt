/*
 * Copyright 2022 Cyface GmbH
 *
 * This file is part of the Serialization.
 *
 * The Serialization is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Serialization is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Serialization. If not, see <http://www.gnu.org/licenses/>.
 */
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

/**
 * A data access object used to write data to a Mongo database.
 *
 * This class encapsulates all the Mongo client code required by the [GridFsStorageService].
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property mongoClient The Vertx [MongoClient] used to access
 */
@Suppress("ForbiddenComment")
// TODO: This should probably also inherit from Database, but I did not find a way to unify the parameters.
open class GridFsDao(private val mongoClient: MongoClient) {

    /**
     * The logger used by objects of this class. Configure it using `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(GridFsDao::class.java)

    /**
     * Creates indices required by this application asynchronously, if they do not yet exist.
     *
     * @return A [Future] that is notified of the success or failure of this application, upon completion.
     */
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

    /**
     * Store the provided [Measurement] to Mongo Grid FS using data from the provided [AsyncFile].
     *
     * @param fileName The filename to use in Grid FS.
     * @return A [Future] that is notified of the success or failure, upon completion of this operation.
     */
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

    /**
     * Check for the existence of a measurement with the provided identifier in the database.
     *
     * @return A [Future] that is notified of success or failure of this operation on completion.
     */
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
