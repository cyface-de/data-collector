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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import de.cyface.collector.commons.MongoTest
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
import io.vertx.core.Vertx
import io.vertx.core.file.OpenOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.assertNotNull

/**
 * An integration test to check whether storing data to an embedded GridFS works as expected.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
class GridFSStorageIT {

    /**
     * The embedded Mongo database test environment.
     */
    private lateinit var mongoTest: MongoTest

    private val uploadFolder = Path("upload-folder")

    /**
     * Start the in memory Mongo database and create the directory for temporary files used before storing uploads
     * to GridFS.
     */
    @BeforeEach
    fun setUp(vertx: Vertx, context: VertxTestContext) {
        mongoTest = MongoTest()
        mongoTest.setUpMongoDatabase()
        vertx.fileSystem().mkdir(uploadFolder.absolutePathString()).onComplete {
            context.completeNow()
        }
    }

    @AfterEach
    fun tearDown() {
        mongoTest.stopMongoDb()
        deleteDirectoryRecursion(uploadFolder)
    }

    @Test
    fun `store a measurement results in a stored measurement`(vertx: Vertx, context: VertxTestContext) {
        val config = mongoTest.clientConfiguration()
            .put("connectTimeoutMS", 3000)
            .put("socketTimeoutMS", 3000)
            .put("waitQueueTimeoutMS", 3000)
            .put("serverSelectionTimeoutMS", 1000)
        // .put("db_name", "cyface") // Attention: in data-provider adding this makes the fixture data "disappear"
        val mongoClient = MongoClient.createShared(vertx, config)
        val fileSystem = vertx.fileSystem()
        val oocut = GridFsStorageService(GridFsDao(mongoClient), vertx.fileSystem(), uploadFolder)

        val testFileURI = GridFSStorageIT::class.java.getResource("/test.bin")?.toURI()?.let { Paths.get(it) }
        assertNotNull(testFileURI)
        fileSystem.open(
            testFileURI.absolutePathString(),
            OpenOptions(),
            context.succeeding {
                val user = User(UUID.randomUUID(), "test-user")
                val uploadIdentifier = UUID.randomUUID()
                val contentRange = ContentRange(0L, 3L, 4L)
                val uploadMetaData = UploadMetaData(user, contentRange, uploadIdentifier, measurement)
                oocut.store(
                    it,
                    uploadMetaData
                ).onComplete(
                    context.succeeding {
                        context.verify {
                            assertThat(it.type, equalTo(StatusType.COMPLETE))
                        }
                        context.completeNow()
                    }
                )
            }
        )
    }

    private val measurement: Measurement
        get() {
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

            return Measurement(
                MeasurementIdentifier(deviceIdentifier, measurementIdentifier),
                DeviceMetaData(operatingSystemVersion, deviceType),
                ApplicationMetaData(applicationVersion, formatVersion),
                MeasurementMetaData(length, locationCount, startLocation, endLocation, modality),
                AttachmentMetaData(0, 0, 0, 0L),
            )
        }

    /**
     * Delete the provided directory and all files and subdirectories within.
     */
    @Throws(IOException::class)
    fun deleteDirectoryRecursion(path: Path) {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.newDirectoryStream(path).use { entries ->
                for (entry in entries) {
                    deleteDirectoryRecursion(entry)
                }
            }
        }
        Files.delete(path)
    }
}
