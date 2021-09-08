/*
 * Copyright 2018-2021 Cyface GmbH
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
package de.cyface.collector.v2;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.TestUtils;
import de.cyface.collector.commons.DataCollectorClient;
import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.handler.FormAttributes;
import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.5
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
     * The amount of locations captured for the test measurement.
     */
    private static final String TEST_MEASUREMENT_LOCATION_COUNT = "10";
    /**
     * The endpoint to authenticate against.
     */
    private static final String LOGIN_UPLOAD_ENDPOINT_PATH = "/api/v2/login";
    /**
     * The endpoint to upload measurements to.
     */
    private static final String MEASUREMENTS_UPLOAD_ENDPOINT_PATH = "/api/v2/measurements";
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
    /**
     * The Vert.x multipart form to upload.
     */
    private MultipartForm form = MultipartForm.create();

    /**
     * Deploys the {@link CollectorApiVerticle} in a test context.
     *
     * @param vertx The <code>Vertx</code> instance to deploy the verticle to
     * @param ctx The test context used to control the test <code>Vertx</code>
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    private void deployVerticle(final Vertx vertx, final VertxTestContext ctx) throws IOException {
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
        try (ServerSocket socket = new ServerSocket(0)) {
            final int mongoPort = socket.getLocalPort();
            socket.close();
            mongoTest.setUpMongoDatabase(mongoPort);
        }
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

        this.form = MultipartForm.create();
        form.attribute(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
        form.attribute(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
        form.attribute(FormAttributes.DEVICE_TYPE.getValue(), "HTC Desire");
        form.attribute(FormAttributes.OS_VERSION.getValue(), "4.4.4");
        form.attribute(FormAttributes.APPLICATION_VERSION.getValue(), "4.0.0-alpha1");
        form.attribute(FormAttributes.LENGTH.getValue(), "200.0");
        form.attribute(FormAttributes.LOCATION_COUNT.getValue(), TEST_MEASUREMENT_LOCATION_COUNT);
        form.attribute(FormAttributes.START_LOCATION_LAT.getValue(), TEST_MEASUREMENT_START_LOCATION_LAT);
        form.attribute(FormAttributes.START_LOCATION_LON.getValue(), TEST_MEASUREMENT_START_LOCATION_LON);
        form.attribute(FormAttributes.START_LOCATION_TS.getValue(), "10000");
        form.attribute(FormAttributes.END_LOCATION_LAT.getValue(), TEST_MEASUREMENT_END_LOCATION_LAT);
        form.attribute(FormAttributes.END_LOCATION_LON.getValue(), TEST_MEASUREMENT_END_LOCATION_LON);
        form.attribute(FormAttributes.END_LOCATION_TS.getValue(), "12000");
        form.attribute(FormAttributes.VEHICLE_TYPE.getValue(), "BICYCLE");
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterAll
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests uploading a file to the Vertx API.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testPostFile(final VertxTestContext context) {
        uploadAndCheckForSuccess(context, "/test.bin");
    }

    /**
     * Tests that uploading a larger file works as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testPostLargeFile(final VertxTestContext context) {
        uploadAndCheckForSuccess(context, "/iphone-neu.ccyf");
    }

    /**
     * Tests that trying to upload something using wrong credentials returns HTTP status code 401 as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void uploadWithWrongCredentials_Returns401(final VertxTestContext context) {
        final HttpRequest<Buffer> builder = client.post(collectorClient.getPort(), "localhost",
                MEASUREMENTS_UPLOAD_ENDPOINT_PATH);
        builder.sendMultipartForm(form, context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on invalid authentication!", ar.statusCode(), is(401));
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
        this.form = MultipartForm.create();
        form.attribute(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
        form.attribute(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
        form.attribute(FormAttributes.DEVICE_TYPE.getValue(), "HTC Desire");
        form.attribute(FormAttributes.OS_VERSION.getValue(), "4.4.4");
        form.attribute(FormAttributes.APPLICATION_VERSION.getValue(), "4.0.0-alpha1");
        form.attribute(FormAttributes.LENGTH.getValue(), "Sir! You are being hacked!");
        form.attribute(FormAttributes.LOCATION_COUNT.getValue(), TEST_MEASUREMENT_LOCATION_COUNT);
        form.attribute(FormAttributes.START_LOCATION_LAT.getValue(), TEST_MEASUREMENT_START_LOCATION_LAT);
        form.attribute(FormAttributes.START_LOCATION_LON.getValue(), TEST_MEASUREMENT_START_LOCATION_LON);
        form.attribute(FormAttributes.START_LOCATION_TS.getValue(), "10000");
        form.attribute(FormAttributes.END_LOCATION_LAT.getValue(), TEST_MEASUREMENT_END_LOCATION_LAT);
        form.attribute(FormAttributes.END_LOCATION_LON.getValue(), TEST_MEASUREMENT_END_LOCATION_LON);
        form.attribute(FormAttributes.END_LOCATION_TS.getValue(), "12000");
        form.attribute(FormAttributes.VEHICLE_TYPE.getValue(), "BICYCLE");

        // Execute
        upload(context, "/test.bin", context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on uploading invalid data!", ar.statusCode(), is(422));
            context.completeNow();
        })));
    }

    /**
     * Uploads a file identified by a test resource location and checks that it was
     * uploaded successfully.
     *
     * @param context The Vert.x test context to use to upload the file
     * @param testFileResourceName A resource name of a file to upload for testing
     */
    private void uploadAndCheckForSuccess(final VertxTestContext context,
            final String testFileResourceName) {

        final var returnedRequestFuture = context.checkpoint();

        upload(context, testFileResourceName, context.succeeding(ar -> context.verify(() -> {
            assertThat("Wrong HTTP status code on uploading data!", ar.statusCode(), is(201));
            returnedRequestFuture.flag();
        })));
    }

    /**
     * Uploads the provided file using an authenticated request. You may listen to the completion of this upload using
     * any of the provided handlers.
     *
     * @param context The test context to use.
     * @param testFileResourceName The Java resource name of a file to upload.
     * @param handler The handler called if the client received a response.
     */
    private void upload(final VertxTestContext context, final String testFileResourceName,
            final Handler<AsyncResult<HttpResponse<Buffer>>> handler) {

        final URL testFileResource = this.getClass().getResource(testFileResourceName);
        Validate.notNull(testFileResource);

        form.binaryFileUpload("fileToUpload",
                String.format(Locale.US, "%s_%s.ccyf", deviceIdentifier, measurementIdentifier),
                testFileResource.getFile(), "application/octet-stream");

        form.binaryFileUpload("eventsFile",
                String.format(Locale.US, "%s_%s.ccyfe", deviceIdentifier, measurementIdentifier),
                testFileResource.getFile(), "application/octet-stream");

        LOGGER.debug("Sending authentication request!");
        TestUtils.authenticate(client, collectorClient.getPort(), LOGIN_UPLOAD_ENDPOINT_PATH,
                context.succeeding(authResponse -> {
                    final var authToken = authResponse.getHeader("Authorization");
                    context.verify(() -> {
                        assertThat("Wrong HTTP status on authentication request!", authResponse.statusCode(), is(200));
                        assertThat("Auth token was missing from authentication request!", authToken,
                                is(notNullValue()));
                    });

                    final var builder = client.post(collectorClient.getPort(), "localhost",
                            MEASUREMENTS_UPLOAD_ENDPOINT_PATH);
                    builder.putHeader("Authorization", "Bearer " + authToken);
                    builder.sendMultipartForm(form, handler);
                }));
    }
}
