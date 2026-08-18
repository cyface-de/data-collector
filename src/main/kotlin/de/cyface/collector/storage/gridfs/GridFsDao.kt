/*
 * Copyright 2022-2024 Cyface GmbH
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

import de.cyface.collector.model.AttachmentIdentifier
import de.cyface.collector.model.FormAttributes
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.Upload
import de.cyface.collector.storage.StoredMetaData
import de.cyface.collector.storage.exception.DuplicatesInDatabase
import de.cyface.collector.storage.lenientDate
import de.cyface.collector.storage.lenientDouble
import de.cyface.collector.storage.lenientInt
import de.cyface.collector.storage.lenientLong
import de.cyface.collector.storage.lenientString
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

        Future.all(measurementIndexCreation, userIndexCreation).onComplete {
            ret.complete(it.result())
        }
        return ret.future()
    }

    /**
     * Store the provided [Upload] to Mongo Grid FS using data from the provided [AsyncFile].
     *
     * @param fileName The filename to use in Grid FS.
     * @return A [Future] that is notified of the success or failure, upon completion of this operation.
     */
    open fun store(upload: Upload, fileName: String, data: AsyncFile): Future<ObjectId> {
        val promise = Promise.promise<ObjectId>()
        val bucketServiceCreationCall = mongoClient.createDefaultGridFsBucketService()
        bucketServiceCreationCall.onFailure(promise::fail)
        bucketServiceCreationCall.onSuccess { gridfs ->
            val options = GridFsUploadOptions()
            options.metadata = upload.toJson()
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

    /**
     * Check for the existence of an attachment with the provided identifier in the database.
     *
     * @return A [Future] that is notified of success or failure of this operation on completion.
     */
    fun exists(deviceId: String, measurementId: Long, attachmentId: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()

        val access = mongoClient.createDefaultGridFsBucketService()
        access.onSuccess { gridFs: MongoGridFsClient ->
            try {
                val query = JsonObject()
                query.put("metadata.deviceId", deviceId)
                query.put("metadata.measurementId", measurementId.toString())
                query.put("metadata.attachmentId", attachmentId.toString())
                val findIds = gridFs.findIds(query)
                findIds.onFailure(ret::fail)
                findIds.onSuccess { ids: List<String> ->
                    try {
                        if (ids.size > 1) {
                            logger.error(
                                "More than one attachment found for did {} mid {} aid {}",
                                deviceId,
                                measurementId,
                                attachmentId
                            )
                            ret.fail(
                                DuplicatesInDatabase(
                                    String.format(
                                        Locale.ENGLISH,
                                        "Found %d datasets with did %s, mid %d and aid %d",
                                        ids.size,
                                        deviceId,
                                        measurementId,
                                        attachmentId
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

    /**
     * Load the metadata of the measurement stored under the provided [identifier].
     *
     * @return A [Future] providing the metadata, or `null` if no such measurement is stored.
     */
    fun metaData(identifier: MeasurementIdentifier): Future<StoredMetaData?> {
        val query = JsonObject()
            .put("metadata.deviceId", identifier.deviceIdentifier.toString())
            .put("metadata.measurementId", identifier.measurementIdentifier.toString())
        return metaData(query)
    }

    /**
     * Load the metadata of the attachment stored under the provided [identifier].
     *
     * @return A [Future] providing the metadata, or `null` if no such attachment is stored.
     */
    fun metaData(identifier: AttachmentIdentifier): Future<StoredMetaData?> {
        val query = JsonObject()
            .put("metadata.deviceId", identifier.deviceIdentifier.toString())
            .put("metadata.measurementId", identifier.measurementIdentifier.toString())
            .put("metadata.attachmentId", identifier.attachmentIdentifier.toString())
        return metaData(query)
    }

    /**
     * Load the metadata of the first entry matching [query].
     *
     * Taking the first match is deliberate, where [exists] rejects several matches as a conflict. As long as the
     * database still holds `v2` data, one measurement legitimately owns one entry per `fileType` (see
     * [createIndices]); those entries describe the same measurement, so either one answers the question asked here.
     * [exists] must be stricter because it guards the write path, where an unexpected second entry means an upload
     * is about to be lost.
     */
    private fun metaData(query: JsonObject): Future<StoredMetaData?> {
        val fields = JsonObject().put("metadata", 1).put("uploadDate", 1)
        return mongoClient.findOne("fs.files", query, fields).map { file -> file?.let(::toStoredMetaData) }
    }

    /**
     * Translate an entry of this storage into the storage independent [StoredMetaData].
     *
     * The translation exists so that callers can compare an incoming upload against the stored one without having to
     * know how this class persists it. Grid FS dictates that shape rather than this application: the upload's own
     * metadata is confined to a `metadata` sub document, `uploadDate` is maintained by Grid FS beside it, and the
     * location timestamps sit one level deeper still, inside the GeoJSON documents that carry the start and end
     * position. Flattening all of that here keeps a storage detail from reaching the callers.
     *
     * The values are read leniently because this collection accumulated entries from arbitrary earlier versions of
     * the software: a field may be missing entirely, or held as a string where it is a number today. The caller is a
     * diagnostic, and an incomplete picture of an old entry still tells it something, whereas an exception would
     * tell it nothing — so anything unreadable becomes `null`.
     */
    private fun toStoredMetaData(file: JsonObject): StoredMetaData {
        val metaData = file.getJsonObject("metadata") ?: JsonObject()
        return StoredMetaData(
            deviceType = metaData.lenientString(FormAttributes.DEVICE_TYPE.value),
            operatingSystemVersion = metaData.lenientString(FormAttributes.OS_VERSION.value),
            applicationVersion = metaData.lenientString(FormAttributes.APPLICATION_VERSION.value),
            formatVersion = metaData.lenientInt(FormAttributes.FORMAT_VERSION.value),
            length = metaData.lenientDouble(FormAttributes.LENGTH.value),
            locationCount = metaData.lenientLong(FormAttributes.LOCATION_COUNT.value),
            startLocationTimestamp = metaData.getJsonObject("start")?.lenientLong("timestamp"),
            endLocationTimestamp = metaData.getJsonObject("end")?.lenientLong("timestamp"),
            modality = metaData.lenientString(FormAttributes.MODALITY.value),
            uploadDate = file.lenientDate("uploadDate"),
        )
    }
}
