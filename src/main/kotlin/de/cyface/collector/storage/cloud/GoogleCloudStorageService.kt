@file:Suppress("AnnotationSpacing")

/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.collector.storage.cloud

import com.google.auth.Credentials
import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import io.vertx.ext.reactivestreams.ReactiveWriteStream
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.UUID

@Suppress("MaxLineLength")
/**
 * A storage service to write data from a Vertx `Pipe` to a Google cloud store.
 *
 * Credentials for the service are provided as described
 * [here](https://cloud.google.com/docs/authentication/application-default-credentials).
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property dao The data access object to write an uploads metadata.
 * @property vertx A Vertx instance of the current Vertx environment.
 * @property credentials The Google Cloud [Credentials] used to authenticate with Google Cloud Storage.
 * For information on how to acquire such an instance see the [Google Cloud documentation]
 * (https://github.com/googleapis/google-auth-library-java/blob/040acefec507f419f6e4ec4eab9645a6e3888a15/samples/snippets/src/main/java/AuthenticateExplicit.java).
 * @property projectIdentifier The Google Cloud project identifier used by this service.
 * @property bucketName The Google Cloud Storage bucket name used to store data to.
 */
class GoogleCloudStorageService(
    private val dao: Database,
    private val vertx: Vertx,
    private val credentials: Credentials,
    private val projectIdentifier: String,
    private val bucketName: String
) : DataStorageService {

    override fun store(
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        uploadIdentifier: UUID,
        metaData: RequestMetaData
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        val targetStream = ReactiveWriteStream.writeStream<Buffer>(Vertx.vertx())
        val cloud = createStorage(uploadIdentifier)
        val subscriber = CloudStorageSubscriber<Buffer>(cloud)

        targetStream.subscribe(subscriber)
        pipe.to(targetStream).onSuccess {
            // if finished store the metadata to Mongo and delete the tmp file.
            if (cloud.bytesUploaded() == contentRange.totalBytes) {
                ret.complete(
                    Status(
                        uploadIdentifier,
                        StatusType.COMPLETE,
                        contentRange.toIndex - contentRange.fromIndex
                    )
                )
                dao.storeMetadata(metaData)
            } else {
                ret.complete(
                    Status(
                        uploadIdentifier,
                        StatusType.INCOMPLETE,
                        contentRange.toIndex - contentRange.fromIndex
                    )
                )
            }
        }.onFailure(ret::fail)

        return ret.future()
    }

    override fun bytesUploaded(uploadIdentifier: UUID): Future<Long> {
        return vertx.executeBlocking {
            val cloud = createStorage(uploadIdentifier)
            it.complete(cloud.bytesUploaded())
        }
    }

    override fun clean(uploadIdentifier: UUID): Future<Void> {
        return vertx.executeBlocking {
            val cloud = createStorage(uploadIdentifier)
            cloud.delete()
            it.complete()
        }
    }

    override fun startPeriodicCleaningOfTempData(
        uploadExpirationTime: Long,
        vertx: Vertx,
        cleanupOperation: CleanupOperation
    ) {
        vertx.setPeriodic(uploadExpirationTime) {
            cleanupOperation.clean(uploadExpirationTime)
        }
    }

    override fun isStored(deviceId: String, measurementId: Long): Future<Boolean> {
        /*
        This solution is incorrect. Since I do not store files in gridFS there will be no metadata either.
        Where should I store the metadata? According to stackoverflow in a separate database. So probably inside the
        mongo database as well.
        See: https://stackoverflow.com/questions/55337912/is-it-possible-to-query-google-cloud-storage-custom-metadata
         */
        return dao.exists(deviceId, measurementId)
    }

    /**
     * Create the actual storage instance used to communicate with the Google Cloud.
     */
    private fun createStorage(uploadIdentifier: UUID): GoogleCloudStorage {
        return GoogleCloudStorage(credentials, projectIdentifier, bucketName, uploadIdentifier)
    }
}

/**
 * A Reactive Streams [Subscriber] to write a Vertx [Buffer] to a [CloudStorage].
 *
 * This must be a Reactive Streams implementation since that standard is currently the only way to stream data from a
 * Vertx [Pipe] to some data storage outside of the control of Vertx. Google Cloud is such a storage outside of Vertx'
 * control, since there is no implementation from Vertx directly.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property cloudStorage The [CloudStorage] to communicate with the Cloud and write the received data.
 */
class CloudStorageSubscriber<in T : Buffer>(
    private val cloudStorage: CloudStorage
) : Subscriber<@UnsafeVariance T> {

    /**
     * The Reactive Streams subscription of this [Subscriber].
     */
    private lateinit var subscription: Subscription

    /**
     * If an error occurred, this property provides further details.
     * If no error occurred, this property remains `null``
     */
    var error: Throwable? = null
    override fun onSubscribe(subscription: Subscription) {
        this.subscription = subscription
        this.subscription.request(1)
    }

    override fun onError(t: Throwable) {
        // Delete content from Google Cloud
        this.subscription.cancel()
        cloudStorage.delete()
        this.error = t
    }

    override fun onComplete() {
        this.subscription.cancel()
    }

    override fun onNext(t: T) {
        // Write to Google Cloud
        cloudStorage.write(t.bytes)
        this.subscription.request(1)
    }
}
