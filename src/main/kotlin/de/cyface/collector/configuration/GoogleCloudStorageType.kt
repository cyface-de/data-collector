/*
 * Copyright 2022-2024 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.configuration

import com.google.auth.oauth2.GoogleCredentials
import de.cyface.collector.storage.DataStorageServiceBuilder
import de.cyface.collector.storage.cloud.GoogleCloudStorageServiceBuilder
import de.cyface.collector.storage.cloud.MongoDatabase
import io.vertx.core.Vertx
import io.vertx.ext.mongo.MongoClient
import java.io.FileInputStream

/**
 * The configuration required to create a [de.cyface.collector.storage.DataStorageService] for Google Cloud Storage.
 *
 * @author Klemens Muthmann
 * @property collectionName The name of a Mongo database collection to store file metadata.
 * @property projectIdentifier The identifier of the Google Cloud project containing the bucket to store the data.
 * @property bucketName The Google Cloud storage bucket to store the data in.
 * @property credentialsFile The location of a file containing the credentials to authenticate with the Google Cloud.
 * @property bufferSize The size of the internal data buffer in bytes.
 *     This is the amount of bytes the system accumulates before sending data to Google.
 *     Low values decrease the possibility of data loss and the memory footprint of the application but increase the
 *     number of requests to Google.
 *     Large values increase the memory footprint and may cause data loss in case of a server crash, but also cause a
 *     much more efficient communication with Google.
 *     A value of 500 KB is recommended.
 */
data class GoogleCloudStorageType(
    val collectionName: String,
    val projectIdentifier: String,
    val bucketName: String,
    val credentialsFile: String,
    private val bufferSize: Int,
) : StorageType {
    override fun dataStorageServiceBuilder(vertx: Vertx, mongoClient: MongoClient): DataStorageServiceBuilder {
        val credentials = FileInputStream(credentialsFile).use { stream -> GoogleCredentials.fromStream(stream) }
        val dao = MongoDatabase(mongoClient, collectionName)
        return GoogleCloudStorageServiceBuilder(
            credentials,
            projectIdentifier,
            bucketName,
            dao,
            vertx,
            bufferSize
        )
    }
}
