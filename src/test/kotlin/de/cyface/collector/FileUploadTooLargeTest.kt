/*
 * Copyright 2021 Cyface GmbH
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

import de.cyface.api.Authenticator
import de.cyface.collector.commons.DataCollectorClient
import de.cyface.collector.commons.MongoTest
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
import org.apache.commons.lang3.Validate
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.UUID
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.notNullValue

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
@ExtendWith(VertxExtension::class)
class FileUploadTooLargeTest {
    /**
     * A client used to connect with the Cyface Data Collector.
     */
    private var collectorClient: DataCollectorClient? = null

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
    private var client: WebClient? = null
    private var vertx: Vertx? = null

    /**
     * Deploys the [CollectorApiVerticle] in a test context.
     *
     * @param vertx The `Vertx` instance to deploy the verticle to
     * @param ctx The test context used to control the test `Vertx`
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    @Throws(IOException::class)
    private fun deployVerticle(vertx: Vertx, ctx: VertxTestContext) {
        this.vertx = vertx
        // FIXME: can we somehow overwrite the @setup method to reuse {@link FileUploadTest}?
        // Set maximal payload size to 1 KB (test upload is 130 KB)
        collectorClient = DataCollectorClient(Authenticator.BYTES_IN_ONE_KILOBYTE)
        client = collectorClient!!.createWebClient(vertx, ctx, mongoTest!!.mongoPort)
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
     * Tests that an upload with a too large payload returns a 422 error.
     *
     * @param context The test context for running `Vertx` under test
     */
    @Test
    fun testPreRequestWithTooLargePayload_Returns422(context: VertxTestContext) {
        preRequest(context, 2, false, UPLOAD_SIZE.toLong(),
            context.succeeding { ar: HttpResponse<Buffer?> ->
                context.verify {
                    MatcherAssert.assertThat(
                        "Wrong HTTP status code when uploading unparsable meta data!", ar.statusCode(),
                        Matchers.`is`(Matchers.equalTo(422))
                    )
                    context.completeNow()
                }
            })
    }

    /**
     * Tests that an upload with a too large payload returns a 422 error.
     *
     * @param context The test context for running `Vertx` under test
     */
    @Test
    fun testUploadWithTooLargePayload_Returns422(context: VertxTestContext) {
        preRequestAndReturnLocation(
            context, 1000 /* fake a small upload to bypass pre-request size check */
        ) { uploadUri: String ->
            upload(context, "/iphone-neu.ccyf", "0.0", UPLOAD_SIZE, false, uploadUri,
                0, (UPLOAD_SIZE - 1).toLong(), UPLOAD_SIZE.toLong(), deviceIdentifier,
                context.succeeding { ar: HttpResponse<Buffer?> ->
                    context.verify {
                        MatcherAssert.assertThat(
                            "Wrong HTTP status code when uploading too large payload!", ar.statusCode(),
                            Matchers.`is`(Matchers.equalTo(422))
                        )
                        context.completeNow()
                    }
                })
        }
    }

    /**
     * Sends a pre-request for an upload using an authenticated request. You may listen to the completion of this
     * request using any of the provided handlers.
     *
     * @param context The test context to use.
     * @param preRequestResponseHandler The handler called if the client received a response.
     */
    private fun preRequest(
        context: VertxTestContext,
        locationCount: Int,
        useInvalidToken: Boolean,
        uploadSize: Long,
        preRequestResponseHandler: Handler<AsyncResult<HttpResponse<Buffer?>>>
    ) {
        LOGGER.debug("Sending authentication request!")
        TestUtils.authenticate(client, collectorClient!!.port, LOGIN_UPLOAD_ENDPOINT_PATH,
            context.succeeding { authResponse: HttpResponse<Buffer?> ->
                val authToken = authResponse.getHeader("Authorization")
                context.verify {
                    assertThat(
                        "Wrong HTTP status on authentication request!",
                        authResponse.statusCode(),
                        `is`(200)
                    )
                    assertThat(
                        "Auth token was missing from authentication request!", authToken,
                        `is`(notNullValue())
                    )
                }

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
                val builder = client!!.post(
                    collectorClient!!.port, "localhost",
                    MEASUREMENTS_UPLOAD_ENDPOINT_PATH
                )
                builder.putHeader("Authorization", "Bearer " + if (useInvalidToken) "invalidToken" else authToken)
                builder.putHeader("Accept-Encoding", "gzip")
                builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
                builder.putHeader("x-upload-content-type", "application/octet-stream")
                builder.putHeader("x-upload-content-length", uploadSize.toString())
                builder.putHeader("Content-Type", "application/json; charset=UTF-8")
                builder.putHeader("Host", "10.0.2.2:8081")
                builder.putHeader("Connection", "Keep-Alive")
                builder.putHeader("content-length", "406") // random metadata length for this test
                builder.sendJson(metaDataBody, preRequestResponseHandler)
            })
    }

    private fun preRequestAndReturnLocation(
        context: VertxTestContext, uploadSize: Long,
        uploadUriHandler: Handler<String>
    ) {
        preRequest(context, 2, false, uploadSize, context.succeeding { res: HttpResponse<Buffer?> ->
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
                val locationPattern = "http://10\\.0\\.2\\.2:8081/api/v3/measurements/\\([a-z0-9]{32}\\)/"
                assertThat(
                    "Wrong HTTP Location header on pre-request!",
                    location,
                    matchesPattern(locationPattern)
                )
                uploadUriHandler.handle(location)
            }
        })
    }

    /**
     * Uploads the provided file using an authenticated request. You may listen to the completion of this upload using
     * any of the provided handlers.
     *
     * @param context The test context to use.
     * @param testFileResourceName The Java resource name of a file to upload.
     * @param length the meter-length of the track
     * @param binarySize number of bytes in the binary to upload
     * @param handler The handler called if the client received a response.
     */
    private fun upload(
        context: VertxTestContext,
        testFileResourceName: String,
        length: String,
        binarySize: Int,
        useInvalidToken: Boolean,
        requestUri: String, from: Long,
        to: Long, total: Long,
        deviceId: String, handler: Handler<AsyncResult<HttpResponse<Buffer?>>>
    ) {
        LOGGER.debug("Sending authentication request!")
        TestUtils.authenticate(client, collectorClient!!.port, LOGIN_UPLOAD_ENDPOINT_PATH,
            context.succeeding { authResponse: HttpResponse<Buffer?> ->
                val authToken = authResponse.getHeader("Authorization")
                context.verify {
                    MatcherAssert.assertThat(
                        "Wrong HTTP status on authentication request!",
                        authResponse.statusCode(),
                        Matchers.`is`(200)
                    )
                    MatcherAssert.assertThat(
                        "Auth token was missing from authentication request!", authToken,
                        Matchers.`is`(Matchers.notNullValue())
                    )
                }
                val testFileResource = this.javaClass.getResource(testFileResourceName)
                Validate.notNull(testFileResource)

                // Upload data (4 Bytes of data)
                val path = requestUri.substring(requestUri.indexOf("/api"))
                val builder = client!!.put(collectorClient!!.port, "localhost", path)
                val jwtBearer = "Bearer " + if (useInvalidToken) "invalidToken" else authToken
                builder.putHeader("Authorization", jwtBearer)
                builder.putHeader("Accept-Encoding", "gzip")
                builder.putHeader("Content-Range", String.format("bytes %d-%d/%d", from, to, total))
                builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
                builder.putHeader("Content-Type", "application/octet-stream")
                builder.putHeader("Host", "localhost:" + collectorClient!!.port)
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
                val file = vertx!!.fileSystem().openBlocking(testFileResource.file, OpenOptions())
                builder.sendStream(file, handler)
            })
    }

    companion object {
        /**
         * Logger used to log messages from this class. Configure it using <tt>src/test/resource/logback-test.xml</tt>.
         */
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
         * The endpoint to authenticate against.
         */
        private const val LOGIN_UPLOAD_ENDPOINT_PATH = "/api/v3/login"

        /**
         * The endpoint to upload measurements to. The parameter `uploadType=resumable` is added automatically by the
         * Google API client library used on Android, so we make sure the endpoints can handle this.
         */
        private const val MEASUREMENTS_UPLOAD_ENDPOINT_PATH = "/api/v3/measurements?uploadType=resumable"

        /**
         * A Mongo database lifecycle handler. This provides the test with the capabilities to run and shutdown a Mongo
         * database for testing purposes.
         */
        private var mongoTest: MongoTest? = null
        private const val UPLOAD_SIZE = 134697

        /**
         * Boots the Mongo database before this test starts.
         *
         * @throws IOException If no socket was available for the Mongo database
         */
        @BeforeAll
        @JvmStatic
        @Throws(IOException::class)
        fun setUpMongoDatabase() {
            mongoTest = MongoTest()
            mongoTest!!.setUpMongoDatabase(Network.freeServerPort(Network.getLocalHost()))
        }

        /**
         * Finishes the mongo database after this test has finished.
         */
        @AfterAll
        @JvmStatic
        fun stopMongoDatabase() {
            mongoTest!!.stopMongoDb()
        }
    }
}