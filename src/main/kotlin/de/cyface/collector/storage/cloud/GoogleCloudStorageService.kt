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

import de.cyface.collector.model.RequestMetaData
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
import io.vertx.core.streams.ReadStream
import io.vertx.ext.reactivestreams.ReactiveWriteStream
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
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
 * @property dao The data access object to write an uploads' metadata.
 * @property vertx A Vertx instance of the current Vertx environment.
 *
 */
class GoogleCloudStorageService(
    private val dao: Database,
    private val vertx: Vertx,
    private val cloudStorageFactory: CloudStorageFactory
) : DataStorageService {

    /**
     * Logger used by objects of this class. Configure it using `/src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(GoogleCloudStorageService::class.java)

    override fun <T : RequestMetaData.MeasurementIdentifier> store(
        sourceData: ReadStream<Buffer>,
        uploadMetaData: UploadMetaData<T>
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        val pipe = sourceData.pipe().endOnSuccess(false)
        val targetStream = ReactiveWriteStream.writeStream<Buffer>(vertx)
        val uploadIdentifier = uploadMetaData.uploadIdentifier
        val cloud = cloudStorageFactory.create(uploadIdentifier)
        val subscriber = CloudStorageSubscriber<Buffer>(cloud, uploadMetaData.contentRange.totalBytes, vertx)
        subscriber.dataWrittenListener = object : CloudStorageSubscriber.DataWrittenListener {
            override fun dataWritten() {
                logger.debug("Finished writing upload: {}.", uploadIdentifier)
                // if finished store the metadata to Mongo and delete the tmp file.
                val bytesUploaded = cloud.bytesUploaded()
                logger.debug("Wrote $bytesUploaded")
                val contentRange = uploadMetaData.contentRange
                logger.debug("Expected {}", contentRange)
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
        }

        logger.debug("Piping ${uploadMetaData.contentRange.totalBytes} bytes to Google Cloud.")
        targetStream.subscribe(subscriber)
        // This End-Handler is crucial!!!. Without the pipe does not provide all the data available.
        sourceData.endHandler { logger.debug("All data read for upload {}!", uploadIdentifier) }
        val pipeToProcess = pipe.to(targetStream)
        pipeToProcess.onSuccess { logger.debug("Pipe finished for upload {}!", uploadIdentifier) }
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

    override fun isStored(deviceId: String, measurementId: Long, attachmentId: Long): Future<Boolean> {
        /*
        This solution is incorrect. Since I do not store files in gridFS there will be no metadata either.
        Where should I store the metadata? According to stackoverflow in a separate database. So probably inside the
        mongo database as well.
        See: https://stackoverflow.com/questions/55337912/is-it-possible-to-query-google-cloud-storage-custom-metadata
         */
        return dao.exists(deviceId, measurementId, attachmentId)
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
 * @property cloudStorage The [CloudStorage] to communicate with the Cloud and write the received data.
 */
class CloudStorageSubscriber<in T : Buffer>(
    private val cloudStorage: CloudStorage,
    private val totalBytes: Long,
    private val vertx: Vertx
) : Subscriber<@UnsafeVariance T> {

    /**
     * Logger used by objects of this class. Configure it using `src/main/resources/logback.xml".
     */
    private val logger = LoggerFactory.getLogger(CloudStorageSubscriber::class.java)

    /**
     * The Reactive Streams subscription of this [Subscriber].
     */
    private lateinit var subscription: Subscription

    /**
     * The number of bytes that have been streamed.
     */
    private var streamedBytes = 0

    /**
     * A listener that is informed if all data received was written successfully.
     */
    var dataWrittenListener: DataWrittenListener? = null

    /**
     * If an error occurred, this property provides further details.
     * If no error occurred, this property remains `null``
     */
    var error: Throwable? = null
    override fun onSubscribe(subscription: Subscription) {
        logger.debug("Subscribing to data!")
        this.subscription = subscription
        this.subscription.request(1)
    }

    override fun onError(t: Throwable) {
        // Delete content from Google Cloud
        logger.error("Error writing data!", t)
        this.subscription.cancel()
        cloudStorage.delete()
        this.error = t
    }

    override fun onComplete() {
        logger.debug("Completed writing data!")
        this.subscription.cancel()
    }

    override fun onNext(t: T) {
        // Write to Google Cloud
        logger.debug("Streaming ${t.bytes.size} Bytes to Google Cloud Storage.")
        streamedBytes += t.bytes.size
        vertx.executeBlocking(
            Callable {
                cloudStorage.write(t.bytes)
                logger.debug("Progress: ${streamedBytes.toDouble() / totalBytes.toDouble() * oneHundredPercent} %")
                if (streamedBytes >= totalBytes) {
                    dataWrittenListener?.dataWritten()
                } else {
                    logger.debug("Requesting new data")
                    this.subscription.request(1)
                }
            }
        ).onFailure {
            logger.error("Failed writing ${t.bytes.size}!")
            onError(it)
        }
    }

    interface DataWrittenListener {
        fun dataWritten()
    }

    companion object {
        /**
         * Constant used to display the upload progress in percent.
         */
        private const val oneHundredPercent = 100.0
    }
}
