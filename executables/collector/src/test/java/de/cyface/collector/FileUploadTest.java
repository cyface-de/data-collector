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
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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
    private static final String UPLOAD_PATH_WITH_INVALID_SESSION = "/api/v3/measurements/(random78901234567890123456789012)/";
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
        upload(context, "/test.bin", "0.0", 4, true, UPLOAD_PATH_WITH_INVALID_SESSION,
                0, 3, 4, deviceIdentifier,
                context.succeeding(ar -> context.verify(() -> {
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
        // Create upload session
        preRequestAndReturnLocation(context, uploadUri -> {

            // Set invalid value for a form attribute
            upload(context, "/test.bin", "Sir! You are being hacked!", 4, false, uploadUri,
                    0, 3, 4, deviceIdentifier,
                    context.succeeding(ar -> context.verify(() -> {
                        assertThat("Wrong HTTP status code when uploading unparsable meta data!", ar.statusCode(),
                                is(equalTo(422)));
                        context.completeNow();
                    })));
        });
    }

    @Test
    public void testUploadStatusWithoutPriorUpload_happyPath(final VertxTestContext context) {
        // Create upload session
        preRequestAndReturnLocation(context, uploadUri -> {

            uploadStatus(context, uploadUri, "bytes */4",
                    context.succeeding(ar -> context.verify(() -> {
                        assertThat("Wrong HTTP status code when asking for upload status!", ar.statusCode(),
                                is(equalTo(308)));
                        final var range = ar.getHeader("Range");
                        // As we did not upload data yet, the server should respond without Range header
                        assertThat("Unexpected Range header!", range, nullValue());
                        context.completeNow();
                    })));
        });
    }

    @Test
    public void testUploadStatusAfterPartialUpload_happyPath(final VertxTestContext context) {
        // Create upload session
        preRequestAndReturnLocation(context, uploadUri -> {

            upload(context, "/test.bin", "0.0", 4, false, uploadUri,
                    0, 3, 8 /* partial */, deviceIdentifier,
                    context.succeeding(ar -> context.verify(() -> {
                        assertThat("Wrong HTTP status code when uploading a file chunk!", ar.statusCode(),
                                is(equalTo(308)));

                        uploadStatus(context, uploadUri, "bytes */8",
                                context.succeeding(res -> context.verify(() -> {
                                    // The upload should be successful, expecting to return 200/201 here
                                    assertThat("Wrong HTTP status code when asking for upload status!",
                                            res.statusCode(),
                                            is(equalTo(308)));

                                    final var range = ar.getHeader("Range");
                                    // The server tells us what data it's holding for us to calculate where to resume
                                    assertThat("Unexpected Range header!", range, is(equalTo("bytes=0-3")));

                                    context.completeNow();
                                })));
                    })));
        });
    }

    @Test
    public void testUploadStatusAfterSuccessfulUpload_happyPath(final VertxTestContext context) {
        // Create upload session
        preRequestAndReturnLocation(context, uploadUri -> {

            upload(context, "/test.bin", "0.0", 4, false, uploadUri,
                    0, 3, 4, deviceIdentifier,
                    context.succeeding(ar -> context.verify(() -> {
                        assertThat("Wrong HTTP status code when uploading unparsable meta data!", ar.statusCode(),
                                is(equalTo(201)));

                        uploadStatus(context, uploadUri, "bytes */4",
                                context.succeeding(res -> context.verify(() -> {
                                    // The upload should be successful, expecting to return 200/201 here
                                    assertThat("Wrong HTTP status code when asking for upload status!",
                                            res.statusCode(),
                                            is(equalTo(200)));

                                    context.completeNow();
                                })));
                    })));
        });
    }

    @Test
    public void testUploadWithInvalidSession_returns404(final VertxTestContext context) {
        upload(context, "/test.bin", "0.0", 4, false, UPLOAD_PATH_WITH_INVALID_SESSION,
                0, 3, 4, deviceIdentifier,
                context.succeeding(ar -> context.verify(() -> {
                    assertThat("Wrong HTTP status code when uploading with invalid session!", ar.statusCode(),
                            is(equalTo(404)));
                    assertThat("Wrong HTTP status message when uploading with invalid session!", ar.statusMessage(),
                            is(equalTo("Not Found")));

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
     * Tests uploading a file to the Vertx API.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void uploadWithWrongDeviceId_Returns422(final VertxTestContext context) {

        preRequestAndReturnLocation(context,
                uploadUri -> upload(context, "/test.bin", "0.0", 4, false, uploadUri,
                        0, 3, 4, "deviceIdHack",
                        context.succeeding(ar -> context.verify(() -> {
                            assertThat("Wrong HTTP status code when uploading with a wrong device id!",
                                    ar.statusCode(), is(equalTo(422)));
                            context.completeNow();
                        }))));
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
                    metaDataBody.put("modality", "BICYCLE");
                    metaDataBody.put("startLocTS", "1503055141000");
                    metaDataBody.put("endLocLat", TEST_MEASUREMENT_END_LOCATION_LAT);
                    metaDataBody.put("osVersion", "testOsVersion");
                    metaDataBody.put("measurementId", measurementIdentifier);
                    metaDataBody.put("formatVersion", "3");

                    // Send Pre-Request
                    final var builder = client.post(collectorClient.getPort(), "localhost",
                            "/api/v3/measurements?uploadType=resumable");
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

        preRequestAndReturnLocation(context,
                uploadUri -> upload(context, testFileResourceName, "0.0", binaryLength, false, uploadUri,
                        0, binaryLength - 1, binaryLength, deviceIdentifier,
                        context.succeeding(ar -> context.verify(() -> {
                            assertThat("Wrong HTTP status code on uploading data!", ar.statusCode(), is(equalTo(201)));
                            returnedRequestFuture.flag();
                        }))));
    }

    private void preRequestAndReturnLocation(final VertxTestContext context, final Handler<String> uploadUriHandler) {

        preRequest(context, 2, false, context.succeeding(res -> context.verify(() -> {
            assertThat("Wrong HTTP status code on happy path pre-request test!", res.statusCode(), is(equalTo(200)));
            final var location = res.getHeader("Location");
            assertThat("Missing HTTP Location header in pre-request response!", location, notNullValue());
            final var locationPattern = "http://10\\.0\\.2\\.2:8081/api/v3/measurements/\\([a-z0-9]{32}\\)/";
            assertThat("Wrong HTTP Location header on pre-request!", location, matchesPattern(locationPattern));

            uploadUriHandler.handle(location);
        })));
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
    private void upload(final VertxTestContext context, final String testFileResourceName, final String length,
            @SuppressWarnings("SameParameterValue") final int binarySize, final boolean useInvalidToken,
            final String requestUri, @SuppressWarnings("SameParameterValue") long from, long to, long total,
            String deviceId,
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
                    final var path = requestUri.substring(requestUri.indexOf("/api"));
                    final var builder = client.put(collectorClient.getPort(), "localhost", path);
                    final var jwtBearer = "Bearer " + (useInvalidToken ? "invalidToken" : authToken);
                    builder.putHeader("Authorization", jwtBearer);
                    builder.putHeader("Accept-Encoding", "gzip");
                    builder.putHeader("Content-Range", String.format("bytes %d-%d/%d", from, to, total));
                    builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)");
                    builder.putHeader("Content-Type", "application/octet-stream");
                    builder.putHeader("Host", "localhost:" + collectorClient.getPort());
                    builder.putHeader("Connection", "Keep-Alive");
                    // If the binary length is not set correctly, the connection is closed and no handler called
                    // [DAT-735]
                    builder.putHeader("content-length", String.valueOf(binarySize));
                    // metaData
                    builder.putHeader("deviceType", "testDeviceType");
                    builder.putHeader("appVersion", "testAppVersion");
                    builder.putHeader("startLocLat", "50.2872300402633");
                    builder.putHeader("locationCount", "2");
                    builder.putHeader("startLocLon", "9.185135040263333");
                    builder.putHeader("length", length);
                    builder.putHeader("endLocLon", "9.492934709138925");
                    builder.putHeader("deviceId", deviceId);
                    builder.putHeader("endLocTS", "1503055141001");
                    builder.putHeader("modality", "BICYCLE");
                    builder.putHeader("startLocTS", "1503055141000");
                    builder.putHeader("endLocLat", "50.59502970913889");
                    builder.putHeader("osVersion", "testOsVersion");
                    builder.putHeader("measurementId", measurementIdentifier);
                    builder.putHeader("formatVersion", "3");

                    final var file = vertx.fileSystem().openBlocking(testFileResource.getFile(), new OpenOptions());
                    builder.sendStream(file, handler);
                }));
    }

    private void uploadStatus(final VertxTestContext context, final String requestUri,
            @SuppressWarnings("SameParameterValue") final String contentRange,
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

                    // Send empty PUT request to ask where to continue the upload
                    final var path = requestUri.substring(requestUri.indexOf("/api"));
                    final var builder = client.put(collectorClient.getPort(), "localhost", path);
                    final var jwtBearer = "Bearer " + authToken;
                    builder.putHeader("Authorization", jwtBearer);
                    builder.putHeader("Accept-Encoding", "gzip");
                    builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)");
                    builder.putHeader("Content-Type", "application/octet-stream"); // really?
                    builder.putHeader("Host", "localhost:" + collectorClient.getPort());
                    builder.putHeader("Connection", "Keep-Alive");
                    // empty body
                    builder.putHeader("content-length", "0");
                    // ask where to continue
                    builder.putHeader("Content-Range", contentRange);
                    // metaData
                    builder.putHeader("deviceType", "testDeviceType");
                    builder.putHeader("appVersion", "testAppVersion");
                    builder.putHeader("startLocLat", "50.2872300402633");
                    builder.putHeader("locationCount", "2");
                    builder.putHeader("startLocLon", "9.185135040263333");
                    builder.putHeader("length", "0.0");
                    builder.putHeader("endLocLon", "9.492934709138925");
                    builder.putHeader("deviceId", deviceIdentifier);
                    builder.putHeader("endLocTS", "1503055141001");
                    builder.putHeader("modality", "BICYCLE");
                    builder.putHeader("startLocTS", "1503055141000");
                    builder.putHeader("endLocLat", "50.59502970913889");
                    builder.putHeader("osVersion", "testOsVersion");
                    builder.putHeader("measurementId", measurementIdentifier);
                    builder.putHeader("formatVersion", "3");

                    builder.send(handler);
                }));
    }
}