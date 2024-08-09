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
 * @property cloudStorageFactory A factory to create a [GoogleCloudStorage] instance on demand.
 *      Since the GoogleCloudStorage depends on an upload identifier it can only be created per upload.
 *      The factory stores all data and does all initialization, that is independent of the individual upload to avoid
 *      doing these tasks on each upload.
 * @property bufferSize The size of the internal data buffer in bytes.
 *     This is the amount of bytes the system accumulates before sending data to Google.
 *     Low values decrease the possibility of data loss and the memory footprint of the application but increase the
 *     number of requests to Google.
 *     Large values increase the memory footprint and may cause data loss in case of a server crash, but also cause a
 *     much more efficient communication with Google.
 *     A value of 500 KB is recommended.
 */
class GoogleCloudStorageService(
    private val dao: Database,
    private val vertx: Vertx,
    private val cloudStorageFactory: CloudStorageFactory,
    private val bufferSize: Int
) : DataStorageService {

    /**
     * Logger used by objects of this class. Configure it using `/src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(GoogleCloudStorageService::class.java)

    // @SuppressWarnings("MagicNumber")
    // private val bufferSize = 500 * 1024 // 500 KB

    override fun store(
        sourceData: ReadStream<Buffer>,
        uploadMetaData: UploadMetaData
    ): Future<Status> {
        val ret = Promise.promise<Status>()
        var buffer = Buffer.buffer()

        val uploadIdentifier = uploadMetaData.uploadIdentifier
        val cloud = cloudStorageFactory.create(uploadIdentifier)
        val subscriber = CloudStorageSubscriber<Buffer>(cloud, uploadMetaData.contentRange.totalBytes, vertx)
        val targetStream = ReactiveWriteStream.writeStream<Buffer>(vertx)
        targetStream.subscribe(subscriber)
        subscriber.dataWrittenListener = object : CloudStorageSubscriber.DataWrittenListener {
            override fun dataWritten() {
                handleDataWritten(cloud, uploadMetaData, ret)
            }
        }

        // Collect chunks from the ReadStream (here: HttpRequest with fixed chunk size of 8 KB).
        // Process and reset buffer if it exceeds a certain size. This reduces number of uploads to Google Cloud.
        sourceData.handler { chunk: Buffer ->
            buffer.appendBuffer(chunk)
            if (buffer.length() >= bufferSize) {
                logger.debug("Processing buffer of size: ${buffer.length()} bytes")
                process(buffer, uploadMetaData, targetStream)
                buffer = Buffer.buffer()
            }
        }

        // Process the final bytes still in the buffer after we read all data.
        sourceData.endHandler {
            if (buffer.length() > 0) {
                logger.debug("Processing final buffer of size ${buffer.length()} bytes")
                process(buffer, uploadMetaData, targetStream)
            }
            logger.debug("All data read")
        }

        sourceData.exceptionHandler { throwable ->
            logger.error("Error during upload for {}: {}", uploadIdentifier, throwable.message)
            ret.fail(throwable)
        }

        return ret.future()
    }

    private fun process(buffer: Buffer, uploadMetaData: UploadMetaData, targetStream: ReactiveWriteStream<Buffer>) {
        targetStream.write(buffer).onSuccess {
            logger.trace("Buffer of size ${buffer.length()} processed successfully")
        }.onFailure {
            val uploadIdentifier = uploadMetaData.uploadIdentifier
            logger.error("Failed to process buffer for upload {}.", uploadIdentifier, it)
        }
    }

    private fun handleDataWritten(cloud: CloudStorage, uploadMetaData: UploadMetaData, resultPromise: Promise<Status>) {
        val uploadIdentifier = uploadMetaData.uploadIdentifier
        val bytesUploaded = cloud.bytesUploaded()
        val contentRange = uploadMetaData.contentRange

        logger.debug("Finished uploading {} bytes of total {}", bytesUploaded, contentRange)

        if (bytesUploaded != contentRange.toIndex + 1) {
            resultPromise.fail(
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
            dao.storeMetadata(uploadMetaData)
                .onSuccess {
                    resultPromise.complete(Status(uploadIdentifier, StatusType.COMPLETE, bytesUploaded))
                }
                .onFailure {
                    resultPromise.fail(it)
                }
        } else {
            resultPromise.complete(Status(uploadIdentifier, StatusType.INCOMPLETE, bytesUploaded))
        }
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
    private var streamedBytes = 0L

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
                logger.debug("Progress: ${streamedBytes.toDouble() / totalBytes.toDouble() * ONE_HUNDRED_PERCENT} %")
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
        private const val ONE_HUNDRED_PERCENT = 100.0
    }
}
