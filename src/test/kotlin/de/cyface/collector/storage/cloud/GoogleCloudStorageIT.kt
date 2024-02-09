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

import com.google.auth.oauth2.GoogleCredentials
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import java.io.FileInputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Check that the `GoogleCloudStorage` works with the true service and not only with mocks.
 *
 * As this test communicates with the Google Cloud it is ignored by default.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
@Disabled("This actually uploads data to the Google Cloud and should only be used if parameters are set correctly.")
class GoogleCloudStorageIT {

    lateinit var storage: GoogleCloudStorage

    /**
     * You must set this before running this test.
     *
     * See https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java
     */
    val credentialsFileLocation: String? = null

    @BeforeEach
    fun setUp() {
        // Authentication can be achieved following the Google Documentation: https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java
        if (credentialsFileLocation.isNullOrEmpty()) {
            fail("Please set CREDENTIALS_FILE_LOCATION before running this test!")
        }
        val credentials = FileInputStream(credentialsFileLocation).use { stream ->
            GoogleCredentials.fromStream(stream)
        }
        storage = GoogleCloudStorage(credentials, "cyface-test", "cyface-bucket", UUID.randomUUID())
    }

    @AfterEach
    fun tearDown() {
        storage.delete()
    }

    @Test
    fun `Check that data is uploaded to Google Cloud Object Storage and Deleted after Completion`() {
        // Act
        storage.write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        storage.write(byteArrayOf(0x06, 0x07, 0x08))

        // Assert
        assertEquals(8L, storage.bytesUploaded())

        val download = storage.download().use { stream -> stream.toByteArray() }

        assertEquals(0x01, download[0])
        assertEquals(0x02, download[1])
        assertEquals(0x03, download[2])
        assertEquals(0x04, download[3])
        assertEquals(0x05, download[4])
        assertEquals(0x06, download[5])
        assertEquals(0x07, download[6])
        assertEquals(0x08, download[7])
    }
}
