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
@file:Suppress("AnnotationSpacing")

package de.cyface.collector.storage.cloud

import de.cyface.collector.storage.CleanupOperation
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.ContentRangeNotMatchingFileSize
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import io.vertx.ext.reactivestreams.ReactiveWriteStream
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable

/**
 * A storage service to write data from a Vertx `Pipe` to a Google cloud store.
 *
 * Credentials for the service are provided as described
 * [here](https://cloud.google.com/docs/authentication/application-default-credentials).
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @property dao The data access object to write an uploads' metadata.
 * @property vertx A Vertx instance of the current Vertx environment.
 *
 */
class GoogleCloudStorageService(
    private val dao: Database,
    private val vertx: Vertx,
    private val cloudStorageFactory: CloudStorageFactory
) : DataStorageService {

    override fun store(
        pipe: Pipe<Buffer>,
        uploadMetaData: UploadMetaData
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        val targetStream = ReactiveWriteStream.writeStream<Buffer>(Vertx.vertx())
        val uploadIdentifier = uploadMetaData.uploadIdentifier
        val cloud = cloudStorageFactory.create(uploadIdentifier)
        val subscriber = CloudStorageSubscriber<Buffer>(cloud)

        targetStream.subscribe(subscriber)
        val pipeToProcess = pipe.to(targetStream)
        pipeToProcess.onSuccess {
            // if finished store the metadata to Mongo and delete the tmp file.
            val bytesUploaded = cloud.bytesUploaded()
            val contentRange = uploadMetaData.contentRange
            if (bytesUploaded != contentRange.toIndex + 1) {
                ret.fail(
                    ContentRangeNotMatchingFileSize(
                        String.format(
                            Locale.getDefault(),
                            "Response: 500, Content-Range (%s) not matching file size (%s)",
                            contentRange,
                            bytesUploaded
                        )
                    )
                )
            } else if (contentRange.totalBytes == contentRange.toIndex + 1) {
                ret.complete(
                    Status(
                        uploadIdentifier,
                        StatusType.COMPLETE,
                        bytesUploaded
                    )
                )
                dao.storeMetadata(uploadMetaData.metaData)
            } else {
                ret.complete(
                    Status(
                        uploadIdentifier,
                        StatusType.INCOMPLETE,
                        bytesUploaded
                    )
                )
            }
        }
        pipeToProcess.onFailure(ret::fail)

        return ret.future()
    }

    override fun bytesUploaded(uploadIdentifier: UUID): Future<Long> {
        return vertx.executeBlocking(
            Callable {
                val cloud = cloudStorageFactory.create(uploadIdentifier)
                cloud.bytesUploaded()
            }
        )
    }

    override fun clean(uploadIdentifier: UUID): Future<Void> {
        return vertx.executeBlocking(
            Callable {
                val cloud = cloudStorageFactory.create(uploadIdentifier)
                cloud.delete()
                return@Callable null
            }
        )
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
}

/**
 * A Reactive Streams [Subscriber] to write a Vertx [Buffer] to a [CloudStorage].
 *
 * This must be a Reactive Streams implementation since that standard is currently the only way to stream data from a
 * Vertx [Pipe] to some data storage outside the control of Vertx. Google Cloud is such a storage outside the control of
 * Vertx, since there is no implementation from Vertx directly.
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
