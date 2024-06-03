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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test

/**
 * Tests for the code writing data to and reading data from Google Cloud Storage.
 * These tests do not actually create a connection but rather make sure, the correct API methods get called.
 *
 * @author Klemens Muthmann
 */
class GoogleCloudStorageTest {

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
}
