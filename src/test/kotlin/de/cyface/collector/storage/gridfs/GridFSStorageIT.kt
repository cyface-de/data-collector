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
import de.cyface.collector.model.Attachment
import de.cyface.collector.model.AttachmentIdentifier
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.Measurement
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.Uploadable
import de.cyface.collector.model.User
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.ApplicationMetaData.Companion.CURRENT_TRANSFER_FILE_FORMAT_VERSION
import de.cyface.collector.model.metadata.AttachmentMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.storage.exception.UploadAlreadyExists
import io.vertx.core.Future
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
import kotlin.test.assertEquals
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
        val mongoClient = mongoClient(vertx)
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

    @Test
    fun `storing the same measurement twice fails with UploadAlreadyExists`(vertx: Vertx, context: VertxTestContext) {
        val dao = GridFsDao(mongoClient(vertx))
        val oocut = GridFsStorageService(dao, vertx.fileSystem(), uploadFolder)
        // Reuse the SAME identifier on both stores so the second insert collides on the
        // unique fs.files index (metadata.deviceId, measurementId, fileType).
        val duplicate = measurement

        dao.createIndices()
            .compose { storeOnce(vertx, oocut, duplicate) }
            .compose { storeOnce(vertx, oocut, duplicate) }
            .onComplete(
                context.failing { failure ->
                    context.verify {
                        assertThat(failure is UploadAlreadyExists, equalTo(true))
                    }
                    context.completeNow()
                }
            )
    }

    @Test
    fun `Loading the metadata of a measurement skips an attachment of that measurement`(
        vertx: Vertx,
        context: VertxTestContext
    ) {
        // Arrange
        val dao = GridFsDao(mongoClient(vertx))
        val oocut = GridFsStorageService(dao, vertx.fileSystem(), uploadFolder)
        // A measurement and its attachment share device and measurement identifier, so a query which only asks for
        // those two answers with whichever of the two Mongo returns first. The attachment is stored first on
        // purpose: an unfiltered query answers in natural order and would hand out the attachment, which is exactly
        // the mistake this test guards against.
        val measurementIdentifier = MeasurementIdentifier(UUID.randomUUID(), 1L)
        val attachmentIdentifier = AttachmentIdentifier(measurementIdentifier.deviceIdentifier, 1L, 7L)

        // Act
        storeOnce(vertx, oocut, attachment(attachmentIdentifier))
            .compose { storeOnce(vertx, oocut, measurement(measurementIdentifier)) }
            .compose { dao.metaData(measurementIdentifier) }
            .onComplete(
                // Assert
                context.succeeding { storedMetaData ->
                    context.verify {
                        val stored = assertNotNull(storedMetaData, "No metadata found for the stored measurement.")
                        assertEquals(MEASUREMENT_LOCATION_COUNT, stored.locationCount)
                    }
                    context.completeNow()
                }
            )
    }

    @Test
    fun `Loading the metadata of an attachment returns that attachment`(vertx: Vertx, context: VertxTestContext) {
        // Arrange
        val dao = GridFsDao(mongoClient(vertx))
        val oocut = GridFsStorageService(dao, vertx.fileSystem(), uploadFolder)
        val measurementIdentifier = MeasurementIdentifier(UUID.randomUUID(), 1L)
        val attachmentIdentifier = AttachmentIdentifier(measurementIdentifier.deviceIdentifier, 1L, 7L)

        // Act
        storeOnce(vertx, oocut, measurement(measurementIdentifier))
            .compose { storeOnce(vertx, oocut, attachment(attachmentIdentifier)) }
            .compose { dao.metaData(attachmentIdentifier) }
            .onComplete(
                // Assert
                context.succeeding { storedMetaData ->
                    context.verify {
                        val stored = assertNotNull(storedMetaData, "No metadata found for the stored attachment.")
                        assertEquals(ATTACHMENT_LOCATION_COUNT, stored.locationCount)
                    }
                    context.completeNow()
                }
            )
    }

    /**
     * Create a client for the embedded test database.
     */
    private fun mongoClient(vertx: Vertx): MongoClient {
        val config = mongoTest.clientConfiguration()
            .put("connectTimeoutMS", 3000)
            .put("socketTimeoutMS", 3000)
            .put("waitQueueTimeoutMS", 3000)
            .put("serverSelectionTimeoutMS", 1000)
        // .put("db_name", "cyface") // Attention: in data-provider adding this makes the fixture data "disappear"
        return MongoClient.createShared(vertx, config)
    }

    /**
     * Open the test fixture and run a single [GridFsStorageService.store], using the provided [uploadable] metadata
     * and a fresh upload identifier.
     */
    private fun storeOnce(vertx: Vertx, oocut: GridFsStorageService, uploadable: Uploadable): Future<Status> {
        val fileSystem = vertx.fileSystem()
        val testFileURI = GridFSStorageIT::class.java.getResource("/test.bin")?.toURI()?.let { Paths.get(it) }
        assertNotNull(testFileURI)
        return fileSystem.open(testFileURI.absolutePathString(), OpenOptions()).compose { asyncFile ->
            val user = User(UUID.randomUUID(), "test-user")
            val uploadIdentifier = UUID.randomUUID()
            val contentRange = ContentRange(0L, 3L, 4L)
            val uploadMetaData = UploadMetaData(user, contentRange, uploadIdentifier, uploadable)
            oocut.store(asyncFile, uploadMetaData)
        }
    }

    private val measurement: Measurement
        get() = measurement(MeasurementIdentifier(UUID.randomUUID(), 1L))

    /**
     * A measurement to store, identified by [identifier] and carrying [MEASUREMENT_LOCATION_COUNT] locations, which
     * tells it apart from an [attachment] stored beside it.
     */
    private fun measurement(identifier: MeasurementIdentifier): Measurement {
        return Measurement(
            identifier,
            DeviceMetaData("15.3.1", "iPhone"),
            ApplicationMetaData("6.0.0", CURRENT_TRANSFER_FILE_FORMAT_VERSION),
            MeasurementMetaData(
                13.0,
                MEASUREMENT_LOCATION_COUNT,
                GeoLocation(1L, 10.0, 10.0),
                GeoLocation(2L, 12.0, 12.0),
                "BICYCLE",
            ),
            AttachmentMetaData(0, 0, 0, 0L),
        )
    }

    /**
     * An attachment to store, identified by [identifier] and carrying [ATTACHMENT_LOCATION_COUNT] locations, which
     * tells it apart from the [measurement] it belongs to.
     */
    private fun attachment(identifier: AttachmentIdentifier): Attachment {
        return Attachment(
            identifier,
            DeviceMetaData("15.3.1", "iPhone"),
            ApplicationMetaData("6.0.0", CURRENT_TRANSFER_FILE_FORMAT_VERSION),
            MeasurementMetaData(
                13.0,
                ATTACHMENT_LOCATION_COUNT,
                GeoLocation(1L, 10.0, 10.0),
                GeoLocation(2L, 12.0, 12.0),
                "BICYCLE",
            ),
            AttachmentMetaData(1, 0, 0, 1024L),
        )
    }

    companion object {
        /**
         * The number of locations reported by the stored measurement. It differs from
         * [ATTACHMENT_LOCATION_COUNT] so a test can tell which of the two entries a query answered with.
         */
        private const val MEASUREMENT_LOCATION_COUNT = 666L

        /**
         * The number of locations reported by the stored attachment, see [MEASUREMENT_LOCATION_COUNT].
         */
        private const val ATTACHMENT_LOCATION_COUNT = 42L
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
