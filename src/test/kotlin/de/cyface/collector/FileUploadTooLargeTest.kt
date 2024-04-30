/*
 * Copyright 2021-2024 Cyface GmbH
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
import de.cyface.collector.commons.DataCollectorClient
import de.cyface.collector.commons.MongoTest
import de.cyface.collector.verticle.CollectorApiVerticle
import de.flapdoodle.embed.process.runtime.Network
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.test.assertNotNull

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 *
 * @author Armin Schnabel
 * @version 1.0.4
 * @since 6.0.0
 */
@ExtendWith(VertxExtension::class)
class FileUploadTooLargeTest {
    /**
     * A client used to connect with the Cyface Data Collector.
     */
    private lateinit var collectorClient: DataCollectorClient

    /**
     * A globally unique identifier of the simulated upload device. The actual value does not really matter.
     */
    private var deviceIdentifier = UUID.randomUUID().toString()

    /**
     * The measurement identifier used for the test measurement. The actual value does not matter that much. It
     * simulates a device wide unique identifier.
     */
    private var measurementIdentifier = 1L.toString()

    /**
     * A `WebClient` to access the test API.
     */
    private lateinit var client: WebClient

    /**
     * Deploys the [de.cyface.collector.verticle.CollectorApiVerticle] in a test context.
     *
     * @param vertx The `Vertx` instance to deploy the verticle to
     * @param ctx The test context used to control the test `Vertx`
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    @Throws(IOException::class)
    private fun deployVerticle(vertx: Vertx, ctx: VertxTestContext) {
        @Suppress("ForbiddenComment")
        // FIXME: can we somehow overwrite the @setup method to reuse {@link FileUploadTest}?
        // Set maximal payload size to 1 KB (test upload is 130 KB)
        collectorClient = DataCollectorClient(CollectorApiVerticle.BYTES_IN_ONE_KILOBYTE)
        mongoTest.setUpMongoDatabase(Network.freeServerPort(Network.getLocalHost()))
        client = collectorClient.createWebClient(vertx, ctx, mongoTest, MockedHandlerBuilder())
    }

    /**
     * Initializes the environment for each test case with a mock Mongo Database and a Vert.x set up for testing.
     *
     * @param vertx A `Vertx` instance injected to be used by this test
     * @param context The context of the test Vert.x
     * @throws IOException Fails the test on unexpected exceptions
     */
    @BeforeEach
    @Throws(IOException::class)
    fun setUp(vertx: Vertx, context: VertxTestContext) {
        deployVerticle(vertx, context)
        deviceIdentifier = UUID.randomUUID().toString()
        measurementIdentifier = 1L.toString()
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterEach // We need a new database for each test execution or else data remains in the database.
    fun shutdown() {
        mongoTest.stopMongoDb()
    }

    /**
     * Tests that an upload with a too large payload returns a 422 error.
     *
     * @param context The test context for running `Vertx` under test
     */
    @Test
    fun testPreRequestWithTooLargePayload_Returns422(context: VertxTestContext) {
        preRequest(
            2,
            UPLOAD_SIZE.toLong(),
            context.succeeding { ar: HttpResponse<Buffer?> ->
                context.verify {
                    assertThat(
                        "Wrong HTTP status code when uploading unparsable meta data!",
                        ar.statusCode(),
                        `is`(equalTo(422))
                    )
                    context.completeNow()
                }
            }
        )
    }

    /**
     * Tests that an upload with a too large payload returns a 422 error.
     *
     * @param vertx The Vert.x instance used to run this test.
     * @param context The test context for running `Vertx` under test.
     */
    @Test
    fun testUploadWithTooLargePayload_Returns422(vertx: Vertx, context: VertxTestContext) {
        preRequestAndReturnLocation(
            context,
            1000 // fake a small upload to bypass pre-request size check
        ) { uploadUri: String ->
            upload(
                vertx,
                "/iphone-neu.ccyf",
                "0.0",
                UPLOAD_SIZE,
                uploadUri,
                0,
                (UPLOAD_SIZE - 1).toLong(),
                UPLOAD_SIZE.toLong(),
                deviceIdentifier,
                context.succeeding { ar: HttpResponse<Buffer?> ->
                    context.verify {
                        assertThat(
                            "Wrong HTTP status code when uploading too large payload!",
                            ar.statusCode(),
                            `is`(equalTo(422))
                        )
                        context.completeNow()
                    }
                }
            )
        }
    }

    /**
     * Sends a pre-request for an upload using an authenticated request. You may listen to the completion of this
     * request using any of the provided handlers.
     *
     * @param preRequestResponseHandler The handler called if the client received a response.
     */
    private fun preRequest(
        @Suppress("SameParameterValue") locationCount: Int,
        uploadSize: Long,
        preRequestResponseHandler: Handler<AsyncResult<HttpResponse<Buffer?>>>
    ) {
        val authToken = "eyTestToken"

        // Assemble payload (metaData)
        val metaDataBody = JsonObject()
        metaDataBody.put("deviceType", "testDeviceType")
        metaDataBody.put("appVersion", "testAppVersion")
        metaDataBody.put("startLocLat", TEST_MEASUREMENT_START_LOCATION_LAT)
        metaDataBody.put("locationCount", locationCount)
        metaDataBody.put("startLocLon", TEST_MEASUREMENT_START_LOCATION_LON)
        metaDataBody.put("length", "0.0")
        metaDataBody.put("endLocLon", TEST_MEASUREMENT_END_LOCATION_LON)
        metaDataBody.put("deviceId", deviceIdentifier)
        metaDataBody.put("endLocTS", "1503055141001")
        metaDataBody.put("modality", "BICYCLE")
        metaDataBody.put("startLocTS", "1503055141000")
        metaDataBody.put("endLocLat", TEST_MEASUREMENT_END_LOCATION_LAT)
        metaDataBody.put("osVersion", "testOsVersion")
        metaDataBody.put("measurementId", measurementIdentifier)
        metaDataBody.put("formatVersion", "3")

        // Send Pre-Request
        val builder = client.post(
            collectorClient.port,
            "localhost",
            MEASUREMENTS_UPLOAD_ENDPOINT_PATH
        )
        builder.putHeader("Authorization", "Bearer $authToken")
        builder.putHeader("Accept-Encoding", "gzip")
        builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        builder.putHeader("x-upload-content-type", "application/octet-stream")
        builder.putHeader("x-upload-content-length", uploadSize.toString())
        builder.putHeader("Content-Type", "application/json; charset=UTF-8")
        builder.putHeader("Host", "10.0.2.2:8081")
        builder.putHeader("Connection", "Keep-Alive")
        builder.putHeader("content-length", "406") // random metadata length for this test
        builder.sendJson(metaDataBody, preRequestResponseHandler)
    }

    private fun preRequestAndReturnLocation(
        context: VertxTestContext,
        @Suppress("SameParameterValue") uploadSize: Long,
        uploadUriHandler: Handler<String>
    ) {
        preRequest(
            2,
            uploadSize,
            context.succeeding { res: HttpResponse<Buffer?> ->
                context.verify {
                    assertThat(
                        "Wrong HTTP status code on happy path pre-request test!",
                        res.statusCode(),
                        `is`(equalTo(200))
                    )
                    val location = res.getHeader("Location")
                    assertThat(
                        "Missing HTTP Location header in pre-request response!",
                        location,
                        notNullValue()
                    )
                    val locationPattern = "http://10\\.0\\.2\\.2:8081/api/v4/measurements/\\([a-z0-9]{32}\\)/"
                    assertThat(
                        "Wrong HTTP Location header on pre-request!",
                        location,
                        matchesPattern(locationPattern)
                    )
                    uploadUriHandler.handle(location)
                }
            }
        )
    }

    /**
     * Uploads the provided file using an authenticated request. You may listen to the completion of this upload using
     * any of the provided handlers.
     *
     * @param vertx The Vert.x instance used to load the test data file.
     * @param testFileResourceName The Java resource name of a file to upload.
     * @param length the meter-length of the track.
     * @param binarySize number of bytes in the binary to upload.
     * @param requestUri The URI to upload the data to.
     * @param from The zero based index of the first byte to send.
     * @param to The zero based index of the last byte to send.
     * @param total The total number of bytes to send. On a happy path test this should be to - from.
     * @param deviceId The worldwide unique device identifier of the uploading device. This is usually a UUID.
     * @param handler The handler called if the client received a response.
     */
    private fun upload(
        vertx: Vertx,
        @Suppress("SameParameterValue") testFileResourceName: String,
        @Suppress("SameParameterValue") length: String,
        @Suppress("SameParameterValue") binarySize: Int,
        requestUri: String,
        @Suppress("SameParameterValue") from: Long,
        @Suppress("SameParameterValue") to: Long,
        @Suppress("SameParameterValue") total: Long,
        deviceId: String,
        handler: Handler<AsyncResult<HttpResponse<Buffer?>>>
    ) {
        val authToken = "eyTestToken"

        val testFileResource = this.javaClass.getResource(testFileResourceName)
        assertNotNull(testFileResource)

        // Upload data (4 Bytes of data)
        val path = requestUri.substring(requestUri.indexOf("/api"))
        val builder = client.put(collectorClient.port, "localhost", path)
        val jwtBearer = "Bearer $authToken"
        builder.putHeader("Authorization", jwtBearer)
        builder.putHeader("Accept-Encoding", "gzip")
        builder.putHeader("Content-Range", String.format(Locale.ENGLISH, "bytes %d-%d/%d", from, to, total))
        builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        builder.putHeader("Content-Type", "application/octet-stream")
        builder.putHeader("Host", "localhost:${collectorClient.port}")
        builder.putHeader("Connection", "Keep-Alive")
        // If the binary length is not set correctly, the connection is closed and no handler called
        // [DAT-735]
        builder.putHeader("content-length", binarySize.toString())
        // metaData
        builder.putHeader("deviceType", "testDeviceType")
        builder.putHeader("appVersion", "testAppVersion")
        builder.putHeader("startLocLat", "50.2872300402633")
        builder.putHeader("locationCount", "2")
        builder.putHeader("startLocLon", "9.185135040263333")
        builder.putHeader("length", length)
        builder.putHeader("endLocLon", "9.492934709138925")
        builder.putHeader("deviceId", deviceId)
        builder.putHeader("endLocTS", "1503055141001")
        builder.putHeader("modality", "BICYCLE")
        builder.putHeader("startLocTS", "1503055141000")
        builder.putHeader("endLocLat", "50.59502970913889")
        builder.putHeader("osVersion", "testOsVersion")
        builder.putHeader("measurementId", measurementIdentifier)
        builder.putHeader("formatVersion", "3")
        val file = vertx.fileSystem().openBlocking(testFileResource.file, OpenOptions())
        builder.sendStream(file, handler)
    }

    companion object {
        /**
         * Logger used to log messages from this class. Configure it using <tt>src/test/resource/logback-test.xml</tt>.
         */
        @Suppress("unused")
        private val LOGGER = LoggerFactory.getLogger(FileUploadTooLargeTest::class.java)

        /**
         * The geographical latitude of the end of the test measurement.
         */
        private const val TEST_MEASUREMENT_END_LOCATION_LAT = "12.0"

        /**
         * The geographical longitude of the end of the test measurement.
         */
        private const val TEST_MEASUREMENT_END_LOCATION_LON = "12.0"

        /**
         * The geographical latitude of the test measurement.
         */
        private const val TEST_MEASUREMENT_START_LOCATION_LAT = "10.0"

        /**
         * The geographical longitude of the test measurement.
         */
        private const val TEST_MEASUREMENT_START_LOCATION_LON = "10.0"

        /**
         * The endpoint to upload measurements to. The parameter `uploadType=resumable` is added automatically by the
         * Google API client library used on Android, so we make sure the endpoints can handle this.
         */
        private const val MEASUREMENTS_UPLOAD_ENDPOINT_PATH = "/api/v4/measurements?uploadType=resumable"

        /**
         * A Mongo database lifecycle handler. This provides the test with the capabilities to run and shutdown a Mongo
         * database for testing purposes.
         */
        private val mongoTest: MongoTest = MongoTest()

        /**
         * A very large payload size, used to test how the system reacts to large uploads.
         */
        private const val UPLOAD_SIZE = 134697
    }
}
