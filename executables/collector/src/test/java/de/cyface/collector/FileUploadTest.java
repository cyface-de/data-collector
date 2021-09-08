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
package de.cyface.collector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.commons.DataCollectorClient;
import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.verticle.CollectorApiVerticle;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.0
 * @since 2.0.0
 */
@ExtendWith(VertxExtension.class)
@SuppressWarnings("PMD.MethodNamingConventions")
public final class FileUploadTest {
    /**
     * Logger used to log messages from this class. Configure it using <tt>src/test/resource/logback-test.xml</tt>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadTest.class);
    /**
     * The geographical latitude of the end of the test measurement.
     */
    private static final String TEST_MEASUREMENT_END_LOCATION_LAT = "12.0";
    /**
     * The geographical longitude of the end of the test measurement.
     */
    private static final String TEST_MEASUREMENT_END_LOCATION_LON = "12.0";
    /**
     * The geographical latitude of the test measurement.
     */
    private static final String TEST_MEASUREMENT_START_LOCATION_LAT = "10.0";
    /**
     * The geographical longitude of the test measurement.
     */
    private static final String TEST_MEASUREMENT_START_LOCATION_LON = "10.0";
    /**
     * The endpoint to authenticate against.
     */
    private static final String LOGIN_UPLOAD_ENDPOINT_PATH = "/api/v3/login";
    /**
     * The endpoint to upload measurements to. The parameter `uploadType=resumable` is added automatically by the
     * Google API client library used on Android, so we make sure the endpoints can handle this.
     */
    private static final String MEASUREMENTS_UPLOAD_ENDPOINT_PATH = "/api/v3/measurements?uploadType=resumable";
    /**
     * A Mongo database lifecycle handler. This provides the test with the capabilities to run and shutdown a Mongo
     * database for testing purposes.
     */
    private static MongoTest mongoTest;
    /**
     * A client used to connect with the Cyface Data Collector.
     */
    private DataCollectorClient collectorClient;
    /**
     * A <code>WebClient</code> to access the test API.
     */
    private WebClient client;
    /**
     * A globally unique identifier of the simulated upload device. The actual value does not really matter.
     */
    private String deviceIdentifier = UUID.randomUUID().toString();
    /**
     * The measurement identifier used for the test measurement. The actual value does not matter that much. It
     * simulates a device wide unique identifier.
     */
    private String measurementIdentifier = String.valueOf(1L);
    private Vertx vertx;

    /**
     * Deploys the {@link CollectorApiVerticle} in a test context.
     *
     * @param vertx The <code>Vertx</code> instance to deploy the verticle to
     * @param ctx The test context used to control the test <code>Vertx</code>
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    private void deployVerticle(final Vertx vertx, final VertxTestContext ctx) throws IOException {
        this.vertx = vertx;
        collectorClient = new DataCollectorClient();
        client = collectorClient.createWebClient(vertx, ctx, mongoTest.getMongoPort());
    }

    /**
     * Boots the Mongo database before this test starts.
     *
     * @throws IOException If no socket was available for the Mongo database
     */
    @BeforeAll
    public static void setUpMongoDatabase() throws IOException {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase(Network.getFreeServerPort());
    }

    /**
     * Initializes the environment for each test case with a mock Mongo Database and a Vert.x set up for testing.
     *
     * @param vertx A <code>Vertx</code> instance injected to be used by this test
     * @param context The context of the test Vert.x
     * @throws IOException Fails the test on unexpected exceptions
     */
    @BeforeEach
    public void setUp(final Vertx vertx, final VertxTestContext context) throws IOException {
        deployVerticle(vertx, context);

        this.deviceIdentifier = UUID.randomUUID().toString();
        this.measurementIdentifier = String.valueOf(1L);
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterAll
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests that sending a pre-request using wrong credentials returns HTTP status code 401 as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void preRequestWithWrongCredentials_Returns401(final VertxTestContext context) {
        preRequest(context, 2, true, context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on invalid authentication!", ar.statusCode(), is(equalTo(401)));
            context.completeNow();
        })));
    }

    /**
     * Tests that trying to upload something using wrong credentials returns HTTP status code 401 as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void uploadWithWrongCredentials_Returns401(final VertxTestContext context) {
        final var returnedRequestFuture = context.checkpoint();
        upload(context, "/test.bin", "0.0", 4, true, context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on invalid authentication!", ar.statusCode(), is(equalTo(401)));
            returnedRequestFuture.flag();
        })));
    }

    /**
     * Tests that sending a pre-request without locations returns HTTP status code 412 as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void preRequestWithoutLocations_Returns412(final VertxTestContext context) {
        preRequest(context, 0, false, context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on pre-request without locations!", ar.statusCode(), is(equalTo(412)));
            context.completeNow();
        })));
    }

    /**
     * Tests that an upload with unparsable metadata returns a 422 error.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testUploadWithUnParsableMetaData_Returns422(final VertxTestContext context) {
        // Set invalid value for a form attribute
        // Execute
        upload(context, "/test.bin", "Sir! You are being hacked!", 4, false,
                context.succeeding(ar -> context.verify(() -> {
                    assertThat("Wrong HTTP status code when uploading unparsable meta data!", ar.statusCode(),
                            is(equalTo(422)));
                    context.completeNow();
                })));
    }

    /**
     * Tests that sending a pre-request without locations returns HTTP status code 412 as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void preRequest_happyPath(final VertxTestContext context) {
        preRequest(context, 2, false, context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on happy path pre-request test!", ar.statusCode(), is(equalTo(200)));
            context.completeNow();
        })));
    }

    /**
     * Tests uploading a file to the Vertx API.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void upload_happyPath(final VertxTestContext context) {
        uploadAndCheckForSuccess(context, "/test.bin", 4);
    }

    /**
     * Tests that uploading a larger file works as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void upload_largeFile(final VertxTestContext context) {
        uploadAndCheckForSuccess(context, "/iphone-neu.ccyf", 134697);
    }

    /**
     * Sends a pre-request for an upload using an authenticated request. You may listen to the completion of this
     * request using any of the provided handlers.
     *
     * @param context The test context to use.
     * @param preRequestResponseHandler The handler called if the client received a response.
     */
    private void preRequest(final VertxTestContext context, final int locationCount,
            @SuppressWarnings("SameParameterValue") final boolean useInvalidToken,
            final Handler<AsyncResult<HttpResponse<Buffer>>> preRequestResponseHandler) {

        LOGGER.debug("Sending authentication request!");
        TestUtils.authenticate(client, collectorClient.getPort(), LOGIN_UPLOAD_ENDPOINT_PATH,
                context.succeeding(authResponse -> {
                    final var authToken = authResponse.getHeader("Authorization");
                    context.verify(() -> {
                        assertThat("Wrong HTTP status on authentication request!", authResponse.statusCode(), is(200));
                        assertThat("Auth token was missing from authentication request!", authToken,
                                is(notNullValue()));
                    });

                    // Assemble payload (metaData)
                    final var metaDataBody = new JsonObject();
                    metaDataBody.put("deviceType", "testDeviceType");
                    metaDataBody.put("appVersion", "testAppVersion");
                    metaDataBody.put("startLocLat", TEST_MEASUREMENT_START_LOCATION_LAT);
                    metaDataBody.put("locationCount", locationCount);
                    metaDataBody.put("startLocLon", TEST_MEASUREMENT_START_LOCATION_LON);
                    metaDataBody.put("length", "0.0");
                    metaDataBody.put("endLocLon", TEST_MEASUREMENT_END_LOCATION_LON);
                    metaDataBody.put("deviceId", deviceIdentifier);
                    metaDataBody.put("endLocTS", "1503055141001");
                    metaDataBody.put("vehicle", "BICYCLE");
                    metaDataBody.put("startLocTS", "1503055141000");
                    metaDataBody.put("endLocLat", TEST_MEASUREMENT_END_LOCATION_LAT);
                    metaDataBody.put("osVersion", "testOsVersion");
                    metaDataBody.put("measurementId", measurementIdentifier);
                    metaDataBody.put("formatVersion", "2");

                    // Send Pre-Request
                    final var builder = client.post(collectorClient.getPort(), "localhost",
                            MEASUREMENTS_UPLOAD_ENDPOINT_PATH);
                    builder.putHeader("Authorization", "Bearer " + (useInvalidToken ? "invalidToken" : authToken));
                    builder.putHeader("Accept-Encoding", "gzip");
                    builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)");
                    builder.putHeader("x-upload-content-type", "application/octet-stream");
                    builder.putHeader("x-upload-content-length", "9522");
                    builder.putHeader("Content-Type", "application/json; charset=UTF-8");
                    builder.putHeader("Host", "10.0.2.2:8081");
                    builder.putHeader("Connection", "Keep-Alive");
                    builder.putHeader("content-length", "406"); // random value, must be correct for the upload
                    builder.sendJson(metaDataBody, preRequestResponseHandler);
                }));
    }

    /**
     * Uploads a file identified by a test resource location and checks that it was
     * uploaded successfully.
     *
     * @param context The Vert.x test context to use to upload the file
     * @param testFileResourceName A resource name of a file to upload for testing
     * @param binaryLength number of bytes in the binary to upload
     */
    private void uploadAndCheckForSuccess(final VertxTestContext context,
            final String testFileResourceName, int binaryLength) {

        final var returnedRequestFuture = context.checkpoint();

        upload(context, testFileResourceName, "0.0", binaryLength, false,
                context.succeeding(ar -> context.verify(() -> {
                    assertThat("Wrong HTTP status code on uploading data!", ar.statusCode(), is(equalTo(201)));
                    returnedRequestFuture.flag();
                })));
    }

    /**
     * Uploads the provided file using an authenticated request. You may listen to the completion of this upload using
     * any of the provided handlers.
     *
     * @param context The test context to use.
     * @param testFileResourceName The Java resource name of a file to upload.
     * @param length the km-length of the track
     * @param binaryLength number of bytes in the binary to upload
     * @param handler The handler called if the client received a response.
     */
    private void upload(final VertxTestContext context, final String testFileResourceName, final String length,
            @SuppressWarnings("SameParameterValue") final int binaryLength, final boolean useInvalidToken,
            final Handler<AsyncResult<HttpResponse<Buffer>>> handler) {

        LOGGER.debug("Sending authentication request!");
        TestUtils.authenticate(client, collectorClient.getPort(), LOGIN_UPLOAD_ENDPOINT_PATH,
                context.succeeding(authResponse -> {
                    final var authToken = authResponse.getHeader("Authorization");
                    context.verify(() -> {
                        assertThat("Wrong HTTP status on authentication request!", authResponse.statusCode(), is(200));
                        assertThat("Auth token was missing from authentication request!", authToken,
                                is(notNullValue()));
                    });
                    final var testFileResource = this.getClass().getResource(testFileResourceName);
                    Validate.notNull(testFileResource);

                    // Upload data (4 Bytes of data)
                    final var builder = client.put(collectorClient.getPort(), "localhost",
                            MEASUREMENTS_UPLOAD_ENDPOINT_PATH);
                    final var jwtBearer = "Bearer " + (useInvalidToken ? "invalidToken" : authToken);
                    builder.putHeader("Authorization", jwtBearer);
                    builder.putHeader("Accept-Encoding", "gzip");
                    builder.putHeader("Content-Range", "bytes 0-3/4");
                    builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)");
                    builder.putHeader("Content-Type", "application/octet-stream");
                    builder.putHeader("Host", "localhost:" + collectorClient.getPort());
                    builder.putHeader("Connection", "Keep-Alive");
                    // If the binary length is not set correctly, the connection is closed and no handler called
                    // [DAT-735]
                    builder.putHeader("content-length", String.valueOf(binaryLength));
                    // metaData
                    builder.putHeader("deviceType", "testDeviceType");
                    builder.putHeader("appVersion", "testAppVersion");
                    builder.putHeader("startLocLat", "50.2872300402633");
                    builder.putHeader("locationCount", "2");
                    builder.putHeader("startLocLon", "9.185135040263333");
                    builder.putHeader("length", length);
                    builder.putHeader("endLocLon", "9.492934709138925");
                    builder.putHeader("deviceId", "testDevi-ce00-42b6-a840-1b70d30094b8");
                    builder.putHeader("endLocTS", "1503055141001");
                    builder.putHeader("vehicle", "BICYCLE");
                    builder.putHeader("startLocTS", "1503055141000");
                    builder.putHeader("endLocLat", "50.59502970913889");
                    builder.putHeader("osVersion", "testOsVersion");
                    builder.putHeader("measurementId", "239");
                    builder.putHeader("formatVersion", "2");

                    final var file = vertx.fileSystem().openBlocking(testFileResource.getFile(), new OpenOptions());
                    // builder.timeout(5000); // To fix TimeoutException which only occurs on the CI
                    builder.sendStream(file, handler);
                }));
    }
}