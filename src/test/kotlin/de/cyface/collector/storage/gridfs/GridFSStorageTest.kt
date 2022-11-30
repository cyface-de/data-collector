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
package de.cyface.collector.storage.gridfs

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.model.RequestMetaData.GeoLocation
import de.cyface.collector.storage.StatusType
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.FileProps
import io.vertx.core.file.FileSystem
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests that storing data to Mongo Grid FS works as expected.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class GridFSStorageTest {
    // Explicit Type Arguments are required by Mockito.
    @Suppress("RemoveExplicitTypeArguments")
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
        val mockFile: AsyncFile = mock()

        val user = User(ObjectId("622a1c7ab7e63734fc40cf49"), "testUser")
        val contentRange = ContentRange(0L, 4L, 5L)
        val uploadIdentifier = UUID.randomUUID()
        val deviceIdentifier = UUID.randomUUID()
        val measurementIdentifier = "1L"
        val operatingSystemVersion = "15.3.1"
        val deviceType = "iPhone"
        val applicationVersion = "6.0.0"
        val length = 13.0
        val locationCount = 666L
        val startLocation = GeoLocation(1L, 10.0, 10.0)
        val endLocation = GeoLocation(2L, 12.0, 12.0)
        val modality = "BICYCLE"
        val formatVersion = RequestMetaData.CURRENT_TRANSFER_FILE_FORMAT_VERSION
        val metaData = RequestMetaData(
            deviceIdentifier.toString(),
            measurementIdentifier,
            operatingSystemVersion,
            deviceType,
            applicationVersion,
            length,
            locationCount,
            startLocation,
            endLocation,
            modality,
            formatVersion
        )
        val oocut = GridFsStorageService(mockMongoClient, fileSystem)

        // Act
        val countDownLatch = CountDownLatch(1)
        val result = oocut.store(mockPipe, user, contentRange, uploadIdentifier, metaData)
        result.onFailure { cause ->
            fail("Failed Storing test data", cause)
        }
        result.onSuccess {
            assertThat(it.type, `is`(StatusType.COMPLETE))
            countDownLatch.countDown()
        }

        // Assert
        argumentCaptor<Handler<AsyncFile>> {
            verify(fsOpenResult).onSuccess(capture())

            firstValue.handle(mockFile)

            argumentCaptor<Handler<Void>> {
                verify(pipeToResultMock).onSuccess(capture())

                firstValue.handle(null)

                argumentCaptor<Handler<FileProps>> {
                    verify(fsPropsResultMock).onSuccess(capture())

                    firstValue.handle(mockFsProps)

                    argumentCaptor<Handler<MongoGridFsClient>> {
                        verify(mockMongoGridFSClientResult).onSuccess(capture())

                        firstValue.handle(mockMongoGridFSClient)

                        argumentCaptor<Handler<AsyncFile>> {
                            verify(temporaryStorageOpenResult).onSuccess(capture())

                            firstValue.handle(mockFile)

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

            val expectedMetaData = metaData.toJson()
            val metadata = firstValue.metadata
            assertThat(metadata.getString("deviceId"), equalTo(expectedMetaData.getString("deviceId")))
            assertThat(metadata.getString("measurementId"), equalTo(expectedMetaData.getString("measurementId")))
        }
    }

    @Test
    fun `Upload file in multiple chunks`() {
        // Arrange
        val mockGridFsClientCreationCall = mock<Future<MongoGridFsClient>> {}
        val mockMongoClient = mock<MongoClient> {
            on { createDefaultGridFsBucketService() } doReturn mockGridFsClientCreationCall
        }
        val mockTemporaryFileOpenCall01 = mock<Future<AsyncFile>> {}
        val mockTemporaryFileOpenCall02 = mock<Future<AsyncFile>> {}
        val mockTemporaryFileOpenCall03 = mock<Future<AsyncFile>> {}
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
        val mockUser = mock<User> {
            on { idString } doReturn "testuser"
        }
        val uploadIdentifier = UUID.randomUUID()
        val metaData = metadata()
        val contentRange01 = ContentRange(0L, 4L, 15L)
        val contentRange02 = ContentRange(5L, 9L, 15L)
        val contentRange03 = ContentRange(10L, 14L, 15L)
        val oocut = GridFsStorageService(mockMongoClient, mockFileSystem)

        // Act
        oocut.store(mockPipe, mockUser, contentRange01, uploadIdentifier, metaData)
        oocut.store(mockPipe, mockUser, contentRange02, uploadIdentifier, metaData)
        oocut.store(mockPipe, mockUser, contentRange03, uploadIdentifier, metaData)

        // Assert
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
                }
            }
        }

        // Verify that the file is actually stored at the end.
        verify(mockMongoClient).createDefaultGridFsBucketService()
    }

    private fun metadata(): RequestMetaData {
        return RequestMetaData(
            deviceIdentifier = "78370516-4f7e-11ed-bdc3-0242ac120002",
            measurementIdentifier = "1",
            operatingSystemVersion = "iOS",
            deviceType = "iPhone16",
            applicationVersion = "3.2.1",
            length = 20.0,
            locationCount = 434,
            startLocation = GeoLocation(512367323L, 51.0, 13.0),
            endLocation = GeoLocation(512377323L, 51.5, 13.2),
            modality = "BICYCLE",
            formatVersion = 3
        )
    }
}