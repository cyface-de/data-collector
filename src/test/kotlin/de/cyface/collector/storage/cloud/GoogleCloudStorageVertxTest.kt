/*
 * Copyright 2024 Cyface GmbH
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

import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.User
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.AttachmentMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.ContentRangeNotMatchingFileSize
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.reactivestreams.FlowAdapters
import java.util.UUID
import java.util.concurrent.SubmissionPublisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This tests the interaction with the classes for Google Cloud storage, while mocking away actual communication with
 * Google.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
class GoogleCloudStorageVertxTest {
    /**
     * This test checks that storing data results in registering the correct stream to the provided `Pipe`.
     * The test for actually writing the data is included
     * [here][de.cyface.collector.storage.cloud.GoogleCloudStorageTest.Happy Path Test for Getting data from a
     * ReactiveStreams Publisher into a CloudStorageSubscriber]
     */
    @Test
    fun `Happy Path Test for Storing some Data`(vertx: Vertx, vertxTestContext: VertxTestContext) {
        // Arrange
        val mockDatabase: MongoDatabase = mock {
            on { storeMetadata(any()) } doReturn Future.succeededFuture("someId")
        }
        val cloudStorage: CloudStorage = mock {
            on { bytesUploaded() } doReturn 10L
        }
        val mockCloudStorageFactory = CloudStorageFactory { cloudStorage }
        val oocut = GoogleCloudStorageService(
            mockDatabase,
            vertx,
            mockCloudStorageFactory
        )
        val exampleImage = this.javaClass.getResource("/example-image.jpg").file
        val readStream = vertx.fileSystem().openBlocking(exampleImage, OpenOptions())
        val user: User = mock()
        val contentRange = ContentRange(0L, 9L, 10L)
        val uploadIdentifier = UUID.randomUUID()
        val metaData = measurement()
        val uploadMetaData = UploadMetaData(user, contentRange, uploadIdentifier, metaData)

        // Act
        val ret = oocut.store(readStream, uploadMetaData)

        // Assert
        ret.onComplete(
            vertxTestContext.succeeding {
                vertxTestContext.verify {
                    assertEquals(10L, ret.result().byteSize)
                    assertEquals(StatusType.COMPLETE, ret.result().type)
                    assertEquals(uploadIdentifier, ret.result().uploadIdentifier)
                }

                vertxTestContext.completeNow()
            }
        )
    }

    /**
     * Tests that uploading a file with a wrong size actually produces the correct error message.
     */
    @Test
    fun `A Mismatch between file size and content range should fail`(vertx: Vertx, vertxTestContext: VertxTestContext) {
        // Arrange
        val database: Database = mock()
        val mockCloudStorage: CloudStorage = mock {
            on { bytesUploaded() } doReturn 4
        }
        val cloudStorageFactory: CloudStorageFactory = mock {
            on { create(any<UUID>()) } doReturn mockCloudStorage
        }
        val oocut = GoogleCloudStorageService(database, vertx, cloudStorageFactory)
        val measurement: UploadMetaData = mock {
            on { contentRange } doReturn ContentRange(5, 7, 3)
            on { uploadIdentifier } doReturn UUID.randomUUID()
        }
        val exampleImage = this.javaClass.getResource("/example-image.jpg").file
        val readStream = vertx.fileSystem().openBlocking(exampleImage, OpenOptions())

        // Act
        val result = oocut.store(readStream, measurement)

        // Assert
        result.onComplete(
            vertxTestContext.failing {
                vertxTestContext.verify {
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

                vertxTestContext.completeNow()
            }
        )
    }

    /**
     * This test simulates what a Vert.x `Pipe` does with a regular `SubmissionPublisher`.
     * It sends two data packages via the publisher and checks that both packages are processed
     * properly by the `CloudStorageSubscriber`.
     */
    @Suppress("MaxLineLength")
    @Test
    fun `Happy Path Test for Getting data from a ReactiveStreams Publisher into a CloudStorageSubscriber`(
        vertx: Vertx,
        vertxTestContext: VertxTestContext
    ) {
        // Arrange
        val cloudStorage: CloudStorage = mock()
        val oocut = CloudStorageSubscriber<Buffer>(cloudStorage, 23L, vertx)
        val publisher = SubmissionPublisher<Buffer>()
        val firstDataPackage = "Hello World!"
        val secondDataPackage = "Hello Mars!"
        oocut.dataWrittenListener = object : CloudStorageSubscriber.DataWrittenListener {
            override fun dataWritten() {
                // Assert
                vertxTestContext.verify {
                    verify(cloudStorage).write(firstDataPackage.toByteArray(Charsets.UTF_8))
                    verify(cloudStorage).write(firstDataPackage.toByteArray(Charsets.UTF_8))
                }
                vertxTestContext.completeNow()
            }
        }

        // Act
        publisher.subscribe(FlowAdapters.toFlowSubscriber(oocut))

        publisher.submit(Buffer.buffer(firstDataPackage))
        publisher.submit(Buffer.buffer(secondDataPackage))
    }

    /**
     * Provide some example metadata usable by tests.
     */
    private fun measurement(): Measurement {
        return Measurement(
            MeasurementIdentifier(
                UUID.fromString("78370516-4f7e-11ed-bdc3-0242ac120002"),
                1L,
            ),
            DeviceMetaData(
                "iOS",
                "iPhone 16",
            ),
            ApplicationMetaData(
                applicationVersion = "3.2.1",
                formatVersion = 3,
            ),
            MeasurementMetaData(
                length = 20.0,
                locationCount = 434,
                startLocation = GeoLocation(512367323L, 51.0, 13.0),
                endLocation = GeoLocation(512377323L, 51.5, 13.2),
                modality = "BICYCLE",
            ),
            AttachmentMetaData(
                logCount = 0,
                imageCount = 0,
                videoCount = 0,
                filesSize = 0L,
            )
        )
    }
}
