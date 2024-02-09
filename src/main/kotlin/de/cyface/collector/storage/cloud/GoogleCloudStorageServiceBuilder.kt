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
 * @version 1.0.1
 * @property credentials The Google Cloud [Credentials] used to authenticate with Google Cloud Storage.
 * For information on how to acquire such an instance see the [Google Cloud documentation]
 * (https://github.com/googleapis/google-auth-library-java/blob/040acefec507f419f6e4ec4eab9645a6e3888a15/samples/snippets/src/main/java/AuthenticateExplicit.java).
 * @property projectIdentifier The Google Cloud project identifier used by the service created from this builder.
 * @property bucketName The name of the Google Cloud Storage bucket used by the created builder to store data.
 * @property dao A data access object to store an uploads metadata.
 * @property vertx The Vertx instance this application runs on.
 * @property pagingSize A parameter used by Google Cloud for the amount of buckets returned per single request.
 * Higher numbers increase the possibility of failure, since requests and responses get larger, but also decrease the
 * number of requests necessary to carry out operations on the Cloud. On stable connections try high numbers on instable
 * ones try lower numbers.
 */
class GoogleCloudStorageServiceBuilder(
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String,
    private val dao: Database,
    private val vertx: Vertx,
    private val pagingSize: Long
) : DataStorageServiceBuilder {
    override fun create(): Future<DataStorageService> {
        val ret = Promise.promise<DataStorageService>()
        vertx.runOnContext {
            val cloudStorageFactory = GoogleCloudStorageFactory(credentials, projectIdentifier, bucketName)
            ret.complete(GoogleCloudStorageService(dao, vertx, cloudStorageFactory))
        }
        return ret.future()
    }

    override fun createCleanupOperation(): CleanupOperation {
        val storage = StorageOptions.newBuilder().setCredentials(credentials)
            .setProjectId(projectIdentifier).build().service
        return GoogleCloudCleanupOperation(storage, pagingSize)
    }
}
