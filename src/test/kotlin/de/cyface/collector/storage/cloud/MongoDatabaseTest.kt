/*
 * Copyright 2025 Cyface GmbH
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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.matches
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.User
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.AttachmentMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.UploadMetaData
import io.vertx.ext.mongo.MongoClient
import io.vertx.kotlin.core.json.get
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.UUID

/**
 * Tests the correct workings of the [MongoDatabase].
 *
 * @author Armin Schnabel
 */
class MongoDatabaseTest {

    @Mock
    lateinit var mongoClient: MongoClient

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `Typed database metadata is consistent`() {
        // Arrange
        val uploadable = Measurement(
            MeasurementIdentifier(UUID.fromString("11111111-1111-1111-1111-111111111111"), 1L),
            DeviceMetaData("Android 14", "sdk_gphone64_x86_64"),
            ApplicationMetaData("0.0.0", 3),
            MeasurementMetaData(
                1.23,
                20L,
                GeoLocation(1000L, 51.1, 13.1),
                GeoLocation(2000L, 51.2, 13.2),
                "BICYCLE"
            ),
            AttachmentMetaData(0, 0, 0, 0L),
        )
        val user = User(UUID.fromString("00000000-0000-0000-0000-000000000000"), "testUser")
        val uploadId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val uploadMetaData = UploadMetaData(
            user,
            ContentRange(0, 4, 5),
            uploadId,
            uploadable
        )
        val oocut = MongoDatabase(mongoClient, "MockedCollection")

        // Act
        val json = oocut.databaseFormat(uploadMetaData)

        // Assert: This ensures the types are correct
        val properties = json.getJsonObject("properties")
        assertThat(properties["filename"], equalTo("33333333-3333-3333-3333-333333333333"))
        assertThat(properties["uploadLength"], equalTo(5L))
        val datePattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,9}Z")
        assertThat(properties.getJsonObject("uploadDate").getString("\$date"), matches(datePattern))
        assertThat(properties["deviceId"], equalTo("11111111-1111-1111-1111-111111111111"))
        assertThat(properties["measurementId"], equalTo("1"))
        assertThat(properties["osVersion"], equalTo("Android 14"))
        assertThat(properties["deviceType"], equalTo("sdk_gphone64_x86_64"))
        assertThat(properties["appVersion"], equalTo("0.0.0"))
        assertThat(properties["formatVersion"], equalTo(3))
        assertThat(properties["length"], equalTo(1.23))
        assertThat(properties["locationCount"], equalTo(20L))
        assertThat(properties["startLocTS"], equalTo(1000L))
        assertThat(properties["endLocTS"], equalTo(2000L))
        val geometry = json.getJsonObject("geometry")
        assertThat(geometry["type"], equalTo("MultiPoint"))
        val coordinates = geometry.getJsonArray("coordinates")
        val startLocation = coordinates.getJsonArray(0)
        assertThat(startLocation.getDouble(0), equalTo(13.1))
        assertThat(startLocation.getDouble(1), equalTo(51.1))
        val endLocation = coordinates.getJsonArray(1)
        assertThat(endLocation.getDouble(0), equalTo(13.2))
        assertThat(endLocation.getDouble(1), equalTo(51.2))
        assertThat(properties["modality"], equalTo("BICYCLE"))
        assertThat(properties["logCount"], equalTo(0))
        assertThat(properties["imageCount"], equalTo(0))
        assertThat(properties["videoCount"], equalTo(0))
        assertThat(properties["filesSize"], equalTo(0L))
        assertThat(properties["userId"], equalTo("00000000-0000-0000-0000-000000000000"))
    }
}
