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
@file:Suppress("AnnotationSpacing")

package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import com.google.cloud.storage.StorageOptions
import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.DataStorageServiceBuilder
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx

@Suppress("MaxLineLength")
/**
 * A [DataStorageServiceBuilder] for a [GoogleCloudStorageService].
 *
 * @author Klemens Muthmann
 * @property credentials The Google Cloud [Credentials] used to authenticate with Google Cloud Storage.
 * For information on how to acquire such an instance see the [Google Cloud documentation]
 * (https://github.com/googleapis/google-auth-library-java/blob/040acefec507f419f6e4ec4eab9645a6e3888a15/samples/snippets/src/main/java/AuthenticateExplicit.java).
 * @property projectIdentifier The Google Cloud project identifier used by the service created from this builder.
 * @property bucketName The name of the Google Cloud Storage bucket used by the created builder to store data.
 * @property dao A data access object for storing uploads' metadata.
 * @property vertx The Vertx instance this application runs on.
 * @property bufferSize The size of the internal data buffer in bytes.
 *     This is the amount of bytes the system accumulates before sending data to Google.
 *     Low values decrease the possibility of data loss and the memory footprint of the application but increase the number of requests to Google.
 *     Large values increase the memory footprint and may cause data loss in case of a server crash, but also cause a much more efficient communication with Google.
 *     A value of 500 KB is recommended.
 */
class GoogleCloudStorageServiceBuilder(
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String,
    private val dao: Database,
    private val vertx: Vertx,
    private val bufferSize: Int,
) : DataStorageServiceBuilder {
    override fun create(): Future<DataStorageService> {
        val ret = Promise.promise<DataStorageService>()
        vertx.runOnContext {
            dao.createIndices().onSuccess {
                val cloudStorageFactory = GoogleCloudStorageFactory(credentials, projectIdentifier, bucketName)
                ret.complete(GoogleCloudStorageService(dao, vertx, cloudStorageFactory, bufferSize))
            }.onFailure {
                ret.fail(it)
            }
        }
        return ret.future()
    }

    override fun createCleanupOperation(): CleanupOperation {
        val storage = StorageOptions.newBuilder().setCredentials(credentials)
            .setProjectId(projectIdentifier).build().service
        return GoogleCloudCleanupOperation(storage, bucketName)
    }
}
