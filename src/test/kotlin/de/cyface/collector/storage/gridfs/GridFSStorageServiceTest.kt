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
package de.cyface.collector.storage.gridfs

import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.User
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.ApplicationMetaData.Companion.CURRENT_TRANSFER_FILE_FORMAT_VERSION
import de.cyface.collector.model.metadata.AttachmentMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.streams.Pipe
import io.vertx.ext.mongo.GridFsUploadOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.mongo.MongoGridFsClient
import org.bson.types.ObjectId
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests that storing data to Mongo Grid FS works as expected.
 *
 * @author Klemens Muthmann
 */
class GridFSStorageServiceTest {
    // Explicit Type Arguments are required by Mockito.
    @Suppress("RemoveExplicitTypeArguments", "RedundantSuppression", "LongMethod")
    @Test
    fun `Happy path for storing a file to GridFS`() {
        // Arrange
        val mockMongoGridFSClientResult: Future<MongoGridFsClient> = mock()
        val mockMongoClient: MongoClient = mock {
            on { createDefaultGridFsBucketService() } doReturn mockMongoGridFSClientResult
        }
        val uploadFileResultMock: Future<String> = mock()
        val mockMongoGridFSClient: MongoGridFsClient = mock {
            on { uploadByFileNameWithOptions(any(), any(), any()) } doReturn uploadFileResultMock
        }
        val fsOpenResult: Future<AsyncFile> = mock()
        val temporaryStorageOpenResult: Future<AsyncFile> = mock()
        val fsPropsResultMock: Future<FileProps> = mock()
        val mockFsProps: FileProps = mock {
            on { size() } doReturn 5
        }
        val fileSystem: FileSystem = mock {
            on { open(anyString(), any()) } doReturn fsOpenResult doReturn temporaryStorageOpenResult
            on { props(anyString()) } doReturn fsPropsResultMock
        }
        val pipeToResultMock: Future<Void> = mock()
        val mockPipe: Pipe<Buffer> = mock {
            on { to(any<AsyncFile>()) } doReturn pipeToResultMock
        }
        val mockFile: AsyncFile = mock {
            on { pipe() } doReturn mockPipe
        }

        val user = User(UUID.randomUUID(), "testUser")
        val contentRange = ContentRange(0L, 4L, 5L)
        val uploadIdentifier = UUID.randomUUID()
        val deviceIdentifier = UUID.randomUUID()
        val measurementIdentifier = 1L
        val operatingSystemVersion = "15.3.1"
        val deviceType = "iPhone"
        val applicationVersion = "6.0.0"
        val length = 13.0
        val locationCount = 666L
        val startLocation = GeoLocation(1L, 10.0, 10.0)
        val endLocation = GeoLocation(2L, 12.0, 12.0)
        val modality = "BICYCLE"
        val formatVersion = CURRENT_TRANSFER_FILE_FORMAT_VERSION
        val measurement = Measurement(
            MeasurementIdentifier(deviceIdentifier, measurementIdentifier),
            DeviceMetaData(operatingSystemVersion, deviceType),
            ApplicationMetaData(applicationVersion, formatVersion),
            MeasurementMetaData(length, locationCount, startLocation, endLocation, modality),
            AttachmentMetaData(0, 0, 0, 0L),
        )
        val oocut = GridFsStorageService(GridFsDao(mockMongoClient), fileSystem, Path.of("upload-folder"))

        // Act
        val countDownLatch = CountDownLatch(1)
        val uploadMetaData = UploadMetaData(user, contentRange, uploadIdentifier, measurement)
        val result = oocut.store(mockFile, uploadMetaData)
        result.onFailure { cause ->
            fail("Failed Storing test data", cause)
        }
        result.onSuccess {
            assertThat(it.type, `is`(StatusType.COMPLETE))
            assertThat(it.byteSize, `is`(5L))
            countDownLatch.countDown()
        }

        // Assert
        argumentCaptor<Handler<AsyncFile>> {
            // Temporary storage successfully opened?
            verify(fsOpenResult).onSuccess(capture())

            firstValue.handle(mockFile)

            argumentCaptor<Handler<Void>> {
                // Data successfully written to temporary storage?
                verify(pipeToResultMock).onSuccess(capture())

                firstValue.handle(null)

                argumentCaptor<Handler<FileProps>> {
                    // Check on whether upload was complete or just a chunk.
                    verify(fsPropsResultMock).onSuccess(capture())

                    firstValue.handle(mockFsProps)

                    argumentCaptor<Handler<AsyncFile>> {
                        verify(temporaryStorageOpenResult).onSuccess(capture())

                        firstValue.handle(mockFile)

                        argumentCaptor<Handler<MongoGridFsClient>> {
                            verify(mockMongoGridFSClientResult).onSuccess(capture())

                            firstValue.handle(mockMongoGridFSClient)

                            argumentCaptor<Handler<String>> {
                                verify(uploadFileResultMock).onSuccess(capture())

                                firstValue.handle("632c541b7021f939b7521e39")
                            }
                        }
                    }
                }
            }
        }
        assertThat(countDownLatch.await(2, TimeUnit.SECONDS), `is`(true))

        argumentCaptor<GridFsUploadOptions> {
            verify(mockMongoGridFSClient).uploadByFileNameWithOptions(any(), any(), capture())

            val expectedMetaData = measurement.toJson()
            val metadata = firstValue.metadata
            assertThat(metadata.getString("deviceId"), equalTo(expectedMetaData.getString("deviceId")))
            assertThat(metadata.getString("measurementId"), equalTo(expectedMetaData.getString("measurementId")))
            // Ensure timestamp is stored as long [RFR-430]
            assertThat(metadata.getJsonObject("start").getLong("timestamp"), equalTo(startLocation.timestamp))
        }
    }

    /**
     * Test that data in multiple chunks is correctly written to temporary storage at first and to GridFS in the end.
     */
    @Test
    @SuppressWarnings("LongMethod")
    fun `Upload file in multiple chunks`() {
        // Arrange
        val storeCall = mock<Future<ObjectId>> {}
        val mockDao = mock<GridFsDao> {
            on { store(any(), anyString(), any()) } doReturn storeCall
        }
        val mockTemporaryFileOpenCall01 = mock<Future<AsyncFile>> {}
        val mockTemporaryFileOpenCall02 = mock<Future<AsyncFile>> {}
        val mockTemporaryFileOpenCall03 = mock<Future<AsyncFile>> {}
        val mockFinalTemporaryFileOpenCall = mock<Future<AsyncFile>> {}
        val mockProps01 = mock<FileProps> {
            on { size() } doReturn 5
        }
        val mockProps02 = mock<FileProps> {
            on { size() } doReturn 10
        }
        val mockProps03 = mock<FileProps> {
            on { size() } doReturn 15
        }
        val mockPropsCall01 = mock<Future<FileProps>> {}
        val mockPropsCall02 = mock<Future<FileProps>> {}
        val mockPropsCall03 = mock<Future<FileProps>> {}
        val mockFileSystem = mock<FileSystem> {
            on { open(any(), any()) }
                .doReturn(mockTemporaryFileOpenCall01)
                .doReturn(mockTemporaryFileOpenCall02)
                .doReturn(mockTemporaryFileOpenCall03)
                .doReturn(mockFinalTemporaryFileOpenCall)
            on { props(any()) } doReturn mockPropsCall01 doReturn mockPropsCall02 doReturn mockPropsCall03
        }
        val mockTemporaryFile = mock<AsyncFile> {}
        val mockStorePipeCall01 = mock<Future<Void>> {}
        val mockStorePipeCall02 = mock<Future<Void>> {}
        val mockStorePipeCall03 = mock<Future<Void>> {}
        val mockPipe = mock<Pipe<Buffer>> {
            on { to(mockTemporaryFile) }
                .doReturn(mockStorePipeCall01)
                .doReturn(mockStorePipeCall02)
                .doReturn(mockStorePipeCall03)
        }
        val mockRequest = mock<HttpServerRequest> {
            on { pipe() } doReturn mockPipe
        }

        @Suppress("SpellCheckingInspection")
        val mockUser = mock<User> {
            on { idString } doReturn "testuser"
        }
        val uploadIdentifier = UUID.randomUUID()
        val measurement = measurement()
        val contentRange01 = ContentRange(0L, 4L, 15L)
        val contentRange02 = ContentRange(5L, 9L, 15L)
        val contentRange03 = ContentRange(10L, 14L, 15L)
        val oocut = GridFsStorageService(mockDao, mockFileSystem, Path.of("upload-folder"))

        // Act
        val uploadMetaData01 = UploadMetaData(mockUser, contentRange01, uploadIdentifier, measurement)
        val storeCall01 = oocut.store(mockRequest, uploadMetaData01)
        val uploadMetaData02 = UploadMetaData(mockUser, contentRange02, uploadIdentifier, measurement)
        val storeCall02 = oocut.store(mockRequest, uploadMetaData02)
        val uploadMetaData03 = UploadMetaData(mockUser, contentRange03, uploadIdentifier, measurement)
        val storeCall03 = oocut.store(mockRequest, uploadMetaData03)

        // Assert
        storeCall01.onSuccess { status ->
            assertThat(status.type, `is`(StatusType.INCOMPLETE))
            assertThat(status.uploadIdentifier, `is`(uploadIdentifier))
            assertThat(status.byteSize, `is`(5L))
        }
        @Suppress("ForbiddenComment")
        storeCall02.onSuccess { status ->
            assertThat(status.type, `is`(StatusType.INCOMPLETE))
            assertThat(status.uploadIdentifier, `is`(uploadIdentifier))
            assertThat(status.byteSize, `is`(10L))
        }
        storeCall03.onSuccess { status ->
            assertThat(status.type, `is`(StatusType.COMPLETE))
            assertThat(status.uploadIdentifier, `is`(uploadIdentifier))
            assertThat(status.byteSize, `is`(15L))
        }

        // Handle first chunk
        argumentCaptor<Handler<AsyncFile>> {
            verify(mockTemporaryFileOpenCall01).onSuccess(capture())

            firstValue.handle(mockTemporaryFile)
            argumentCaptor<Handler<Void>> {
                verify(mockStorePipeCall01).onSuccess(capture())
                firstValue.handle(null)

                argumentCaptor<Handler<FileProps>> {
                    verify(mockPropsCall01).onSuccess(capture())
                    firstValue.handle(mockProps01)
                }
            }
        }

        // Handle second chunk
        argumentCaptor<Handler<AsyncFile>> {
            verify(mockTemporaryFileOpenCall02).onSuccess(capture())

            firstValue.handle(mockTemporaryFile)
            argumentCaptor<Handler<Void>> {
                verify(mockStorePipeCall02).onSuccess(capture())
                firstValue.handle(null)

                argumentCaptor<Handler<FileProps>> {
                    verify(mockPropsCall02).onSuccess(capture())
                    firstValue.handle(mockProps02)
                }
            }
        }

        // Handle third and final chunk
        argumentCaptor<Handler<AsyncFile>> {
            verify(mockTemporaryFileOpenCall03).onSuccess(capture())

            firstValue.handle(mockTemporaryFile)
            argumentCaptor<Handler<Void>> {
                verify(mockStorePipeCall03).onSuccess(capture())
                firstValue.handle(null)

                argumentCaptor<Handler<FileProps>> {
                    verify(mockPropsCall03).onSuccess(capture())
                    firstValue.handle(mockProps03)

                    argumentCaptor<Handler<AsyncFile>> {
                        verify(mockFinalTemporaryFileOpenCall).onSuccess(capture())
                        firstValue.handle(mockTemporaryFile)
                    }
                }
            }
        }

        // Verify that the file is actually stored at the end.
        verify(mockDao).store(any(), anyString(), any())
    }

    private fun measurement(): Measurement {
        return Measurement(
            MeasurementIdentifier(UUID.fromString("78370516-4f7e-11ed-bdc3-0242ac120002"), 1L),
            DeviceMetaData("iOS", "iPhone16"),
            ApplicationMetaData("3.2.1", 3),
            MeasurementMetaData(
                20.0,
                434,
                GeoLocation(512367323L, 51.0, 13.0),
                GeoLocation(512377323L, 51.5, 13.2),
                "BICYCLE",
            ),
            AttachmentMetaData(0, 0, 0, 0L),
        )
    }
}
