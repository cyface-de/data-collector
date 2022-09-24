package de.cyface.collector.storage

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import de.cyface.api.model.User
import de.cyface.collector.commons.MongoTest
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.flapdoodle.embed.process.runtime.Network
import io.vertx.core.Vertx
import io.vertx.core.file.OpenOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.InetAddress
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.test.assertNotNull

@ExtendWith(VertxExtension::class)
class GridFSStorageIT {

    lateinit var mongoTest: MongoTest

    @BeforeEach
    fun setUp(context: VertxTestContext) {
        mongoTest = MongoTest()
        mongoTest.setUpMongoDatabase(Network.freeServerPort(InetAddress.getLocalHost()))
        context.completeNow()
    }

    @AfterEach
    fun tearDown() {
        mongoTest.stopMongoDb()
    }

    @Test
    fun `store a measurement results in a stored measurement`(vertx: Vertx, context: VertxTestContext) {
        val config = mongoTest.clientConfiguration()
            .put("connectTimeoutMS", 3000)
            .put("socketTimeoutMS", 3000)
            .put("waitQueueTimeoutMS", 3000)
            .put("serverSelectionTimeoutMS", 1000)
            //.put("connection_string", "mongodb://localhost:${mongoTest.mongoPort}")
            .put("db_name", "cyface")
        val mongoClient = MongoClient.createShared(vertx, config)
        val fileSystem = vertx.fileSystem()
        val oocut = GridFsStorageService(mongoClient, vertx.fileSystem())


        val testFileURI = GridFSStorageIT::class.java.getResource("/test.bin")?.toURI()?.let { Paths.get(it) }
        assertNotNull(testFileURI)
        fileSystem.open(testFileURI.absolutePathString(), OpenOptions(), context.succeeding {
            val pipe = it.pipe()
            val user = User(ObjectId("622a1c7ab7e63734fc40cf49"), "test-user")
            val uploadIdentifier = UUID.randomUUID()
            val contentRange = ContentRange("0", "3", "4")
            oocut.store(pipe, user, contentRange, uploadIdentifier, metaData).onComplete(context.succeeding {
                context.verify {
                    assertThat(it.type, equalTo(StatusType.COMPLETE))
                }
                context.completeNow()
            })
        })
    }

    private val metaData: RequestMetaData
        get() {
            val deviceIdentifier = UUID.randomUUID()
            val measurementIdentifier = "1L"
            val operatingSystemVersion = "15.3.1"
            val deviceType = "iPhone"
            val applicationVersion = "6.0.0"
            val length = 13.0
            val locationCount = 666L
            val startLocation = RequestMetaData.GeoLocation(1L, 10.0, 10.0)
            val endLocation = RequestMetaData.GeoLocation(2L, 12.0, 12.0)
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
                formatVersion)

            return metaData
        }
}