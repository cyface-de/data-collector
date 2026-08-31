/*
 * Copyright 2022-2025 Cyface GmbH
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

import de.cyface.collector.model.AttachmentIdentifier
import de.cyface.collector.model.FormAttributes
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.storage.StoredMetaData
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.DuplicatesInDatabase
import de.cyface.collector.storage.lenientDate
import de.cyface.collector.storage.lenientDouble
import de.cyface.collector.storage.lenientInt
import de.cyface.collector.storage.lenientLong
import de.cyface.collector.storage.lenientString
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.IndexOptions
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
     * Transforms [UploadMetaData] into the database format.
     *
     * @param metaData The [UploadMetaData] to format.
     * @return The GeoJSON representation of the [UploadMetaData].
     */
    fun databaseFormat(metaData: UploadMetaData): JsonObject {
        val json = metaData.uploadable.toGeoJson()

        json.getJsonObject("properties")
            .put(USER_ID_DATABASE_FIELD, metaData.user.id.toString())
            .put("filename", metaData.uploadIdentifier.toString()) // The id/name of the file in the object storage
            .put("uploadLength", metaData.contentRange.totalBytes) // measurement blob size in Bytes (Long)
            .put("uploadDate", JsonObject().put("\$date", DateTimeFormatter.ISO_INSTANT.format(Instant.now())))

        return json
    }

    /**
     * Checks if the provided combination of deviceIdentifier and measurementIdentifier is already stored in the
     * database.
     */
    override fun exists(deviceIdentifier: String, measurementIdentifier: Long): Future<Boolean> {
        val ret = Promise.promise<Boolean>()
        val query = JsonObject()
            .put("properties.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
            .put("properties.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier.toString())
            // Ensure we don't interpret attachments as measurements
            .put("properties.${FormAttributes.ATTACHMENT_ID.value}", JsonObject().put("\$exists", false))

        val queryCall = mongoClient.find(collectionName, query)
        queryCall.onSuccess { ids ->
            try {
                if (ids.size > 1) { // Should not be possible anymore with collection index
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
            .put("properties.${FormAttributes.DEVICE_ID.value}", deviceIdentifier)
            .put("properties.${FormAttributes.MEASUREMENT_ID.value}", measurementIdentifier.toString())
            .put("properties.${FormAttributes.ATTACHMENT_ID.value}", attachmentId.toString())

        val queryCall = mongoClient.find(collectionName, query)
        queryCall.onSuccess { ids ->
            try {
                if (ids.size > 1) { // Should not be possible anymore with collection index
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

    override fun metaData(identifier: MeasurementIdentifier): Future<StoredMetaData?> {
        val query = JsonObject()
            .put("properties.${FormAttributes.DEVICE_ID.value}", identifier.deviceIdentifier.toString())
            .put("properties.${FormAttributes.MEASUREMENT_ID.value}", identifier.measurementIdentifier.toString())
            // Ensure we don't interpret attachments as measurements
            .put("properties.${FormAttributes.ATTACHMENT_ID.value}", JsonObject().put("\$exists", false))
        return metaData(query)
    }

    override fun metaData(identifier: AttachmentIdentifier): Future<StoredMetaData?> {
        val query = JsonObject()
            .put("properties.${FormAttributes.DEVICE_ID.value}", identifier.deviceIdentifier.toString())
            .put("properties.${FormAttributes.MEASUREMENT_ID.value}", identifier.measurementIdentifier.toString())
            .put("properties.${FormAttributes.ATTACHMENT_ID.value}", identifier.attachmentIdentifier.toString())
        return metaData(query)
    }

    /**
     * Load the metadata of the document matching [query].
     *
     * Asking for a single document is safe because the unique indices created by [createIndices] permit at most one
     * document per identifier. That is also why this does not repeat the duplicate detection [exists] performs:
     * [exists] guards the write path, where a second document would mean losing an upload, while a caller reading
     * metadata back gains nothing from that distinction.
     */
    private fun metaData(query: JsonObject): Future<StoredMetaData?> {
        val fields = JsonObject().put("properties", 1)
        return mongoClient.findOne(collectionName, query, fields).map { document ->
            document?.let(::toStoredMetaData)
        }
    }

    /**
     * Translate a document of this database into the storage independent [StoredMetaData].
     *
     * The translation exists so that callers can compare an incoming upload against the stored one without having to
     * know how this class persists it. An upload is kept as a GeoJSON feature here, a shape chosen for storing and
     * querying geodata rather than for that comparison: the descriptive values sit in the feature's `properties`
     * next to entries that concern nobody outside this class, such as the name of the file in the object storage.
     * Reducing the document to the values a caller actually compares keeps that choice a private matter.
     *
     * The values are read leniently because this collection accumulated documents from arbitrary earlier versions of
     * the software: a field may be missing entirely, or held as a string where it is a number today. The caller is a
     * diagnostic, and an incomplete picture of an old document still tells it something, whereas an exception would
     * tell it nothing — so anything unreadable becomes `null`.
     */
    private fun toStoredMetaData(document: JsonObject): StoredMetaData {
        val properties = document.getJsonObject("properties") ?: JsonObject()
        return StoredMetaData(
            deviceType = properties.lenientString(FormAttributes.DEVICE_TYPE.value),
            operatingSystemVersion = properties.lenientString(FormAttributes.OS_VERSION.value),
            applicationVersion = properties.lenientString(FormAttributes.APPLICATION_VERSION.value),
            formatVersion = properties.lenientInt(FormAttributes.FORMAT_VERSION.value),
            length = properties.lenientDouble(FormAttributes.LENGTH.value),
            locationCount = properties.lenientLong(FormAttributes.LOCATION_COUNT.value),
            startLocationTimestamp = properties.lenientLong(FormAttributes.START_LOCATION_TS.value),
            endLocationTimestamp = properties.lenientLong(FormAttributes.END_LOCATION_TS.value),
            modality = properties.lenientString(FormAttributes.MODALITY.value),
            uploadDate = properties.lenientDate("uploadDate"),
        )
    }

    override fun createIndices(): Future<CompositeFuture> {
        val ret = Promise.promise<CompositeFuture>()

        // Create indices
        val unique = IndexOptions().unique(true)
        val measurementIndex = JsonObject().put("properties.deviceId", 1).put("properties.measurementId", 1)
        val measurementFilter = JsonObject().put("properties.attachmentId", JsonObject().put("\$exists", false))
        val measurementIndexCreation = mongoClient.createIndexWithOptions(
            collectionName,
            measurementIndex,
            unique.partialFilterExpression(measurementFilter)
        )
        val attachmentIndex = JsonObject()
            .put("properties.deviceId", 1)
            .put("properties.measurementId", 1)
            .put("properties.attachmentId", 1)
        val attachmentFilter = JsonObject().put("properties.attachmentId", JsonObject().put("\$exists", true))
        val attachmentIndexCreation = mongoClient.createIndexWithOptions(
            collectionName,
            attachmentIndex,
            unique.partialFilterExpression(attachmentFilter)
        )

        Future.all(measurementIndexCreation, attachmentIndexCreation).onComplete {
            ret.complete(it.result())
        }

        return ret.future()
    }

    companion object {
        /**
         * The field name for the database entry which contains the user id.
         */
        private const val USER_ID_DATABASE_FIELD = "userId"
    }
}
