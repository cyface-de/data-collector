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
import java.io.FileInputStream
import java.util.UUID
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Check that the `GoogleCloudStorage` works with the true service and not only with mocks.
 *
 * As this test communicates with the Google Cloud it is ignored by default.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
@Ignore
class GoogleCloudStorageIT {

    lateinit var storage: GoogleCloudStorage

    @BeforeEach
    fun setUp() {
        val credentialsFile = "/Users/muthmann/.config/gcloud/application_default_credentials.json"
        val credentials = FileInputStream(credentialsFile).use { stream ->
            GoogleCredentials.fromStream(stream)
        }
        storage = GoogleCloudStorage(credentials, "cyface-test", "cyface-bucket", UUID.randomUUID() )
    }

    @AfterEach
    fun tearDown() {
       storage.delete()
    }
    @Test
    fun `Check that data is uploaded to Google Cloud Object Storage and Deleted after Completion`() {
        // Authentication can be achieved following the Google Documentation: https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java


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