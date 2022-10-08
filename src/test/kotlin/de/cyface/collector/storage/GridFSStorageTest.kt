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
package de.cyface.collector.storage

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.model.RequestMetaData.GeoLocation
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
        val contentRange = ContentRange("0", "4", "5")
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
}
