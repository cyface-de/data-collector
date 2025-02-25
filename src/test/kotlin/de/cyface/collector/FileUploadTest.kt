/*
 * Copyright 2021-2025 Cyface GmbH
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
package de.cyface.collector

import de.cyface.collector.auth.MockedHandlerBuilder
import de.cyface.collector.commons.DataCollectorApi
import de.cyface.collector.commons.MongoTest
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException
import java.net.URL
import java.util.UUID

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
// This warning is suppressed since it is wrong for Vert.x Tests.
@ExtendWith(VertxExtension::class)
class FileUploadTest {
    /**
     * A client used to connect with the Cyface Data Collector.
     */
    private lateinit var collectorApi: DataCollectorApi

    /**
     * The measurement identifier used for the test measurement. The actual value does not matter that much. It
     * simulates a device wide unique identifier.
     */
    private var measurementIdentifier = MeasurementIdentifier(UUID.randomUUID(), 1L)

    /**
     * A Mongo database lifecycle handler. This provides the test with the capabilities to run and shutdown a Mongo
     * database for testing purposes.
     */
    private lateinit var mongoTest: MongoTest

    /**
     * Deploys the [de.cyface.collector.verticle.CollectorApiVerticle] in a test context.
     *
     * @param vertx The `Vertx` instance to deploy the verticle to
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    @Throws(IOException::class)
    private suspend fun deployVerticle(vertx: Vertx) {
        collectorApi = DataCollectorApi(140_000L, vertx)
        mongoTest = MongoTest()
        mongoTest.setUpMongoDatabase()
        collectorApi.runCollectorApi(vertx, mongoTest, MockedHandlerBuilder())
    }

    /**
     * Initializes the environment for each test case with a mock Mongo Database and a Vert.x set up for testing.
     *
     * @param vertx A `Vertx` instance injected to be used by this test
     * @throws IOException Fails the test on unexpected exceptions
     */
    @BeforeEach
    @Throws(IOException::class)
    fun setUp(vertx: Vertx) {
        runBlocking {
            deployVerticle(vertx)
        }
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterEach // We need a new database for each test execution or else data remains in the database.
    fun stopMongoDatabase() {
        runBlocking {
            mongoTest.stopMongoDb()
        }
    }

    /**
     * Tests that sending a pre-request without locations returns HTTP status code 412 as expected.
     * This cannot use the [DataCollectorApi] as that requires a correctly formated MetaData object.
     */
    @Test
    fun preRequestWithoutLocations_Returns412() = runTest {
        // Arrange
        val client = collectorApi.client
        val authToken = "eyTestToken"
        val metaDataBody = json {
            obj(
                "deviceType" to "testDeviceType",
                "appVersion" to "testAppVersion",
                "length" to 0.0,
                "locationCount" to 0L,
                "deviceId" to UUID.randomUUID().toString(),
                "modality" to "BICYCLE",
                "osVersion" to "testOsVersion",
                "measurementId" to 1L,
                "formatVersion" to "3"
            )
        }
        val builder = client.post(collectorApi.port, "localhost", "/measurements?uploadType=resumable")
        builder.putHeader("Authorization", "Bearer $authToken")
        builder.putHeader("Accept-Encoding", "gzip")
        builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        builder.putHeader("x-upload-content-type", "application/octet-stream")
        builder.putHeader("x-upload-content-length", metaDataBody.toString().length.toString())
        builder.putHeader("Content-Type", "application/json; charset=UTF-8")
        builder.putHeader("Host", "10.0.2.2:8081")
        builder.putHeader("Connection", "Keep-Alive")
        builder.putHeader("content-length", "406") // random value, must be correct for the upload

        // Act
        val response = builder.sendJson(metaDataBody).coAwait()

        // Assert
        assertThat(response.statusCode(), `is`(412))
    }

    /**
     * Tests that an upload with unparsable metadata returns a 422 error.
     *
     * This could happen for example if someone fakes a valid pre request and later tries to send a faked upload.
     *
     * @param vertx The `Vertx` instance used by this test.
     */
    @Test
    fun testUploadWithUnParsableMetaData_Returns422(vertx: Vertx) = runTest {
        // Create upload session
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )

        // Set invalid value for a form attribute
        val response = collectorApi.upload(
            vertx,
            "/test.bin",
            "Sir! You are being hacked!",
            4,
            uploadUri,
            0,
            3,
            4,
            measurementIdentifier
        )
        assertThat("Wrong HTTP status code when uploading unparsable meta data!", response.statusCode(), `is`(422))
    }

    @Test
    fun testUploadStatusWithoutPriorUpload_happyPath() = runTest {
        // Create upload session
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        val response = collectorApi.uploadStatus(uploadUri, "bytes */4", measurementIdentifier)
        assertThat(
            "Wrong HTTP status code when asking for upload status!",
            response.statusCode(),
            `is`(equalTo(308))
        )
        val range = response.getHeader("Range")
        // As we did not upload data yet, the server should respond without Range header
        assertThat("Unexpected Range header!", range, Matchers.nullValue())
    }

    @Test
    fun testUploadStatusAfterPartialUpload_happyPath(vertx: Vertx) = runTest {
        // Create upload session
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        val uploadResponse = collectorApi.upload(
            vertx,
            "/test.bin",
            "0.0",
            4,
            uploadUri,
            0,
            3,
            8, // partial
            measurementIdentifier,
        )
        assertThat(
            "Wrong HTTP status code when uploading a file chunk!",
            uploadResponse.statusCode(),
            `is`(equalTo(308)),
        )
        val statusResponse = collectorApi.uploadStatus(
            uploadUri,
            "bytes */8",
            measurementIdentifier,
        )
        // The upload should be successful, expecting to return 200/201 here
        assertThat(
            "Wrong HTTP status code when asking for upload status!",
            statusResponse.statusCode(),
            `is`(equalTo(308)),
        )
        val range = statusResponse.getHeader("Range")
        // The server tells us what data it's holding for us to calculate where to resume
        assertThat(
            "Unexpected Range header!",
            range,
            `is`(equalTo("bytes=0-3"))
        )
    }

    @Test
    fun `Test happy path for uploads returns HTTP Status 200`(vertx: Vertx) = runTest {
        val testFileResourceName = "/test.bin"
        val length = "0.0"
        val binarySize = 4
        val from = 0L
        val to = 3L
        val total = 4L
        // Create upload session
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        val uploadResponse = collectorApi.upload(
            vertx,
            testFileResourceName,
            length,
            binarySize,
            uploadUri,
            from,
            to,
            total,
            measurementIdentifier,
        )
        assertThat(
            "Wrong HTTP status code when uploading unparsable meta data!",
            uploadResponse.statusCode(),
            `is`(equalTo(201))
        )

        val statusResponse = collectorApi.uploadStatus(
            uploadUri,
            "bytes */4",
            measurementIdentifier
        )
        // The upload should be successful, expecting to return 200/201 here
        assertThat(
            // "Wrong HTTP status code when asking for upload status!",
            statusResponse.statusCode(),
            `is`(equalTo(200))
        )

        checkStoredMeta(vertx)
    }

    private suspend fun checkStoredMeta(vertx: Vertx) {
        // Ensure the metadata was stored in the correct format
        val mongoClient = MongoClient.createShared(
            vertx,
            mongoTest.clientConfiguration(),
            mongoTest.clientConfiguration().getString("data_source_name")
        )
        val result = mongoClient.findOne("fs.files", JsonObject(), JsonObject()).coAwait()
        assertThat(result.getString("filename").length, `is`(greaterThan(0)))
        assertThat(result.getLong("length"), `is`(equalTo(4L)))
        assertThat(result.getJsonObject("uploadDate").containsKey("\$date"), `is`(equalTo(true)))
        val meta = result.getJsonObject("metadata")
        assertThat(meta.getString("deviceId"), `is`(equalTo(measurementIdentifier.deviceIdentifier.toString())))
        assertThat(
            meta.getString("measurementId"),
            `is`(equalTo(measurementIdentifier.measurementIdentifier.toString()))
        )
        assertThat(meta.getString("osVersion"), `is`(equalTo("testOsVersion")))
        assertThat(meta.getString("deviceType"), `is`(equalTo("testDeviceType")))
        assertThat(meta.getString("appVersion"), `is`(equalTo("testAppVersion")))
        assertThat(meta.getString("formatVersion"), `is`(equalTo("3")))
        assertThat(meta.getInteger("length"), `is`(equalTo(0)))
        assertThat(meta.getLong("locationCount"), `is`(equalTo(2L)))
        assertThat(meta.getJsonObject("start").containsKey("location"), `is`(equalTo(true)))
        assertThat(meta.getJsonObject("end").containsKey("location"), `is`(equalTo(true)))
        assertThat(meta.getString("modality"), `is`(equalTo("BICYCLE")))
        assertThat(meta.getInteger("logCount"), `is`(equalTo(0)))
        assertThat(meta.getInteger("imageCount"), `is`(equalTo(0)))
        assertThat(meta.getInteger("videoCount"), `is`(equalTo(0)))
        assertThat(meta.getLong("filesSize"), `is`(equalTo(0L)))
        assertThat(UUID.fromString(meta.getString("userId")), `is`(notNullValue()))
    }

    /**
     * Tests that an upload to an invalid session returns HTTP status code 404.
     *
     * @param vertx The `Vertx` instance used by this test.
     */
    @Test
    fun testUploadWithInvalidSession_returns404(vertx: Vertx) = runTest {
        val uploadPathWithInvalidSession = URL("http://localhost:8081/measurements/(random78901234567890123456789012)/")
        val uploadResponse = collectorApi.upload(
            vertx,
            "/test.bin",
            "0.0",
            4,
            uploadPathWithInvalidSession,
            0,
            3,
            4,
            measurementIdentifier,
        )

        assertThat(
            "Wrong HTTP status code when uploading with invalid session!",
            uploadResponse.statusCode(),
            `is`(equalTo(404))
        )
        assertThat(
            "Wrong HTTP status message when uploading with invalid session!",
            uploadResponse.statusMessage(),
            `is`(equalTo("Not Found"))
        )
    }

    /**
     * Tests that sending a pre-request returns HTTP status code 200 as expected.
     */
    @Test
    fun preRequest_happyPath() = runTest {
        val response = collectorApi.preRequest(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        assertThat(
            "Wrong HTTP status code on happy path pre-request test!",
            response.statusCode(),
            `is`(equalTo(200))
        )
    }

    /**
     * Tests uploading a file to the Vertx API.
     *
     * @param vertx The `Vertx` instance used by this test.
     */
    @Test
    fun upload_happyPath(vertx: Vertx) = runTest {
        val testFileResourceName = "/test.bin"
        val binaryLength = 4
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        val uploadResponse = collectorApi.upload(
            vertx,
            testFileResourceName,
            "0.0",
            binaryLength,
            uploadUri,
            0,
            (binaryLength - 1).toLong(),
            binaryLength.toLong(),
            measurementIdentifier,
        )

        assertThat(
            "Wrong HTTP status code on uploading data!",
            uploadResponse.statusCode(),
            `is`(equalTo(201))
        )
    }

    /**
     * Tests that uploading a larger file works as expected.
     *
     * @param vertx The `Vertx` instance used by this test.
     */
    @Test
    fun upload_largeFile(vertx: Vertx) = runTest {
        val testFileResourceName = "/iphone-neu.ccyf"
        val binaryLength = 134697
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        val response = collectorApi.upload(
            vertx,
            testFileResourceName,
            "0.0",
            binaryLength,
            uploadUri,
            0,
            (binaryLength - 1).toLong(),
            binaryLength.toLong(),
            measurementIdentifier,
        )

        assertThat(
            "Wrong HTTP status code on uploading data!",
            response.statusCode(),
            `is`(equalTo(201))
        )
    }

    /**
     * Tests uploading a file to the Vertx API.
     *
     * @param vertx The `Vertx` instance used by this test.
     */
    @Test
    fun uploadWithWrongDeviceId_Returns422(vertx: Vertx) = runTest {
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2L,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE",
            ),
            deviceMetaData = DeviceMetaData(
                operatingSystemVersion = "testOsVersion",
                deviceType = "testDeviceType",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "testAppVersion",
                formatVersion = 3,
            ),
        )
        val response = collectorApi.upload(
            vertx,
            "/test.bin",
            "0.0",
            4,
            uploadUri,
            0,
            3,
            4,
            "deviceIdHack",
            "1L"
        )

        assertThat(
            "Wrong HTTP status code when uploading with a wrong device id!",
            response.statusCode(),
            `is`(equalTo(422)),
        )
    }
}
