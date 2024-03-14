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
package de.cyface.collector.storage.cloud

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BucketListOption
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.model.User
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.ContentRangeNotMatchingFileSize
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import io.vertx.core.streams.WriteStream
import io.vertx.ext.reactivestreams.ReactiveWriteStream
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.reactivestreams.FlowAdapters
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the code writing data to and reading data from Google Cloud Storage.
 * These tests do not actually create a connection but rather make sure, the correct API methods get called.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 */
class GoogleCloudStorageTest {

    /**
     * This test simulates what a Vert.x `Pipe` does with a regular `SubmissionPublisher`.
     * It sends two data packages via the publisher and checks that both packages are processed
     * properly by the `CloudStorageSubscriber`.
     */
    @Test
    fun `Happy Path Test for Getting data from a ReactiveStreams Publisher into a CloudStorageSubscriber`() {
        // Arrange
        val cloudStorage: CloudStorage = mock()
        val oocut = CloudStorageSubscriber<Buffer>(cloudStorage)
        val publisher = SubmissionPublisher<Buffer>()
        val firstDataPackage = "Hello World!"
        val secondDataPackage = "Hello Mars!"
        val lock = CountDownLatch(1)

        // Act
        publisher.subscribe(FlowAdapters.toFlowSubscriber(oocut))
        publisher.subscribe(object : Flow.Subscriber<Buffer> {
            override fun onSubscribe(subscription: Flow.Subscription?) {
                // Nothing to do here.
            }

            override fun onError(throwable: Throwable?) {
                // Nothing to do here.
            }

            override fun onComplete() {
                lock.countDown()
            }

            override fun onNext(item: Buffer) {
                // Nothing to do here.
            }
        })
        publisher.submit(Buffer.buffer(firstDataPackage))
        publisher.submit(Buffer.buffer(secondDataPackage))

        lock.await(5L, TimeUnit.SECONDS)
        // Assert
        verify(cloudStorage).write(firstDataPackage.toByteArray(Charsets.UTF_8))
        verify(cloudStorage).write(firstDataPackage.toByteArray(Charsets.UTF_8))
    }

    /**
     * This test checks that storing data results in registering the correct stream to the provided `Pipe`.
     * The test for actually writing the data is included
     * [here][de.cyface.collector.storage.cloud.GoogleCloudStorageTest.Happy Path Test for Getting data from a
     * ReactiveStreams Publisher into a CloudStorageSubscriber]
     */
    @Test
    fun `Happy Path Test for Storing some Data`() {
        // Arrange
        val mockDatabase: MongoDatabase = mock()
        val cloudStorage: CloudStorage = mock {
            on { bytesUploaded() } doReturn 10L
        }
        val mockCloudStorageFactory = CloudStorageFactory { cloudStorage }
        val oocut = GoogleCloudStorageService(
            mockDatabase,
            Vertx.vertx(),
            mockCloudStorageFactory
        )
        val resultFutureMock: Future<Void> = mock()
        val toFuture: Future<Void> = mock {
            on { onSuccess(any()) } doReturn resultFutureMock
        }
        val pipe: Pipe<Buffer> = mock {
            on { to(any()) } doReturn toFuture
        }
        val user: User = mock()
        val contentRange = ContentRange(0L, 9L, 10L)
        val uploadIdentifier = UUID.randomUUID()
        val metaData: RequestMetaData = metadata()
        val uploadMetaData = UploadMetaData(user, contentRange, uploadIdentifier, metaData)

        // Act
        val ret = oocut.store(pipe, uploadMetaData)

        // Assert
        verify(pipe).to(any<ReactiveWriteStream<Buffer>>())
        argumentCaptor<Handler<Void>> {
            verify(toFuture).onSuccess(capture())

            firstValue.handle(null)
        }

        assertTrue(ret.isComplete)
        assertTrue(ret.succeeded())
        assertEquals(10L, ret.result().byteSize)
        assertEquals(StatusType.COMPLETE, ret.result().type)
        assertEquals(uploadIdentifier, ret.result().uploadIdentifier)
    }

    /**
     * Check that [GoogleCloudCleanupOperation] cleans only old files.
     */
    @Test
    fun `Periodic Data Cleaning should Delete Stale Files`() {
        // Arrange
        val testUploadIdentifier = UUID.randomUUID().toString()
        val testTemporaryFile = "$testUploadIdentifier.tmp"
        val testDataFile = "$testUploadIdentifier.data"
        val currentTime = OffsetDateTime.now()
        val fileExpirationTime = 100L
        val rejectTime = currentTime.minus(fileExpirationTime + 1L, ChronoUnit.MILLIS)
        val acceptTime = currentTime.minus(fileExpirationTime / 2, ChronoUnit.MILLIS)
        val mockBucket01 = mock<Bucket> {
            on { updateTimeOffsetDateTime } doReturn rejectTime
            on { name } doReturn testTemporaryFile
        }
        val mockBucket02 = mock<Bucket> {
            on { updateTimeOffsetDateTime } doReturn acceptTime
            on { name } doReturn testDataFile
        }
        val mockStorage: Storage = mock {
            on { get(any<String>()) } doReturn mockBucket01
            on { list(any<BucketListOption>()) } doReturn object : Page<Bucket> {

                private val data = mutableListOf(mockBucket01, mockBucket02)

                override fun hasNextPage(): Boolean {
                    throw NotImplementedError("Nothing to do here!")
                }

                override fun getNextPageToken(): String {
                    throw NotImplementedError("Nothing to do here!")
                }

                override fun getNextPage(): Page<Bucket> {
                    throw NotImplementedError("Nothing to do here!")
                }

                override fun iterateAll(): MutableIterable<Bucket> {
                    return data
                }

                override fun getValues(): MutableIterable<Bucket> {
                    throw NotImplementedError("Nothing to do here!")
                }
            }
        }
        val oocut = GoogleCloudCleanupOperation(mockStorage, "test")

        // Act
        oocut.clean(fileExpirationTime)

        // Assert
        verify(mockStorage).delete(testTemporaryFile)
        verify(mockStorage).delete(testDataFile)
    }

    /**
     * Tests that uploading a file with a wrong size actually produces the correct error message.
     */
    @Test
    fun `A Mismatch between file size and content range should fail`() {
        // Arrange
        val database: Database = mock()
        val vertx: Vertx = mock()
        val mockCloudStorage: CloudStorage = mock {
            on { bytesUploaded() } doReturn 4
        }
        val cloudStorageFactory: CloudStorageFactory = mock {
            on { create(any<UUID>()) } doReturn mockCloudStorage
        }
        val oocut = GoogleCloudStorageService(database, vertx, cloudStorageFactory)
        val mockToFuture: Future<Void> = mock()
        val mockPipe: Pipe<Buffer> = mock {
            on { to(any<WriteStream<Buffer>>()) } doReturn mockToFuture
        }
        val metadata: UploadMetaData = mock {
            on { contentRange } doReturn ContentRange(5, 7, 3)
            on { uploadIdentifier } doReturn UUID.randomUUID()
        }

        // Act
        val result = oocut.store(mockPipe, metadata)

        // Assert
        argumentCaptor<Handler<Void>> {
            verify(mockToFuture).onSuccess(capture())

            firstValue.handle(null)
        }
        assertTrue(result.isComplete)
        assertTrue(result.failed())
        assertEquals(
            ContentRangeNotMatchingFileSize(
                """
                Response: 500, Content-Range (ContentRange(fromIndex=5, toIndex=7, totalBytes=3)) not matching file size (4)
                """.trim()
            ),
            result.cause(),
        )
    }

    /**
     * Provide some example metadata usable by tests.
     */
    private fun metadata(): RequestMetaData {
        return RequestMetaData(
            deviceIdentifier = "78370516-4f7e-11ed-bdc3-0242ac120002",
            measurementIdentifier = "1",
            operatingSystemVersion = "iOS",
            deviceType = "iPhone16",
            applicationVersion = "3.2.1",
            length = 20.0,
            locationCount = 434,
            startLocation = RequestMetaData.GeoLocation(512367323L, 51.0, 13.0),
            endLocation = RequestMetaData.GeoLocation(512377323L, 51.5, 13.2),
            modality = "BICYCLE",
            formatVersion = 3
        )
    }
}
