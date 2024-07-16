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
package de.cyface.collector.storage.cloud

import de.cyface.collector.model.FormAttributes
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.DuplicatesInDatabase
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A [Database] implementation to store metadata into a Mongo database.
 *
 * @author Klemens Muthmann
 * @property mongoClient The Vertx [MongoClient] used to access the Mongo database.
 * @property collectionName The name of the collection to store the metadata under.
 */
class MongoDatabase(private val mongoClient: MongoClient, private val collectionName: String) : Database {

    /**
     * The logger used by instances of this class. Configure it using `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(MongoDatabase::class.java)

    override fun storeMetadata(
        metaData: UploadMetaData
    ): Future<String> {
        val document = databaseFormat(metaData)
        return mongoClient.insert(collectionName, document)
    }

    /**
     * Formats the provided [UploadMetaData] into a [JsonObject] which can be stored in the database.
     *
     * Currently, we maintain a schema consistent with the one used in GridFS storage to leverage existing toolchains.
     * Note that `chunkSize` is omitted as blobs are stored as single files in the object store.
     *
     * TODO: Discussion:
     * - Should we keep the old schema like in fs.files?
     *   - Preferred until a clear use case necessitates a change.
     *   - Allows reuse of existing tools for deserialization and checks.
     *   - Question: Where to store filenames and sizes of attachments?
     *     - Option 1: In document.attachments: [{filename: "", length: 123, type: "log" or "image"}]
     *     - Option 2: As a separate document with the same meta as measurement but with metadata.attachmentId
     *       - Preferred for querying images for a specific region. (How will we actually access attachments?)
     * - Alternatively, should we use GeoJSON?
     *   - Advantage: Enables spatial queries for measurements/attachments.
     *   - Measurement: GeoJSON with start-end as geometry.
     *   - Attachment: GeoJSON with file geo ref as geometry or as currently implemented (measurement).
     *   - Challenge: Where to store additional metadata like filename and size in bytes? See current schema.
     * - Or ... we currently use StorageType "folder" for measurements in object storage.
     *   - does it make sense to store the attachments in there? What would be the benefit?
     *   - but we still cannot quickly check just with this structure if an attachment with id X exists
     *   - the file metadata in object storage does not seem to be made for this according to a comment of K w/source:
     *   https://stackoverflow.com/questions/55337912/is-it-possible-to-query-google-cloud-storage-custom-metadata
     *
     * @param metaData The [UploadMetaData] to format.
     * @return The [JsonObject] representation of the [UploadMetaData].
     */
    fun databaseFormat(metaData: UploadMetaData): JsonObject {
        val isoDate = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val storageMeta = metaData.uploadable.toJson()
            .put(USER_ID_DATABASE_FIELD, metaData.user.id.toString())
        return JsonObject()
            .put("filename", metaData.uploadIdentifier.toString()) // The id/name of the file in the object storage
            .put("length", metaData.contentRange.totalBytes) // blob size in Bytes (Long)
            .put("uploadDate", JsonObject().put("\$date", isoDate))
            .put("metadata", storageMeta)
    }

    /**
     * Checks if the provided combination of deviceIdentifier and measurementIdentifier is already stored in the
     * database.
     */
    override fun exists(deviceIdentifier: String, measurementIdentifier: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()
        val query = JsonObject()
            .put("metadata.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
            .put("metadata.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier.toString())
            // Ensure we don't interpret attachments as measurements
            .put("metadata.${FormAttributes.ATTACHMENT_ID.value}", JsonObject().put("\$exists", false))
        //query.put("features.0.properties.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
        //query.put("features.0.properties.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier)

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

    /**
     * Checks if the provided combination of deviceIdentifier, measurementIdentifier and attachmentId is already stored
     * in the database.
     */
    override fun exists(deviceIdentifier: String, measurementIdentifier: Long, attachmentId: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()
        val query = JsonObject()
            .put("metadata.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
            .put("metadata.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier.toString())
            .put("metadata.${FormAttributes.ATTACHMENT_ID.value}", attachmentId.toString())
        //query.put("features.0.properties.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
        //query.put("features.0.properties.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier)
        //query.put("features.0.properties.${FormAttributes.ATTACHMENT_ID.value}", attachmentId)

        val queryCall = mongoClient.find(collectionName, query)
        queryCall.onSuccess { ids ->
            try {
                if (ids.size > 1) {
                    logger.error(
                        "More than one attachment found for did {} mid {} aid {}",
                        deviceIdentifier,
                        measurementIdentifier,
                        attachmentId
                    )
                    ret.fail(
                        DuplicatesInDatabase(
                            String.format(
                                Locale.ENGLISH,
                                "Found %d datasets with did %s, mid %d and aid %d",
                                ids.size,
                                deviceIdentifier,
                                measurementIdentifier,
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
        queryCall.onFailure(ret::fail)

        return ret.future()
    }

    companion object {
        /**
         * The field name for the database entry which contains the user id.
         */
        private const val USER_ID_DATABASE_FIELD = "userId"
    }
}
