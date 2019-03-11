/*
 * Copyright 2018 Cyface GmbH
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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.collector.handler.FormAttributes;
import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 * 
 * @author Klemens Muthmann
 * @version 3.0.0
 * @since 2.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class FileUploadTest {
    /**
     * Logger used to log messages from this class. Configure it using <tt>src/test/resource/logback-test.xml</tt>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadTest.class);
    /**
     * The endpoint to upload measurements to.
     */
    private static final String MEASUREMENTS_UPLOAD_ENDPOINT_PATH = "/api/v2/measurements";
    /**
     * The test <code>Vertx</code> instance.
     */
    private Vertx vertx;
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
     * A globally unqiue identifier of the simulated upload device. The actual value does not really matter.
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
     * @param ctx The test context used to control the test <code>Vertx</code>.
     * @throws IOException Fails the test if anything unexpected goes wrong.
     */
    private void deployVerticle(final TestContext ctx) throws IOException {
        collectorClient = new DataCollectorClient();
        vertx = Vertx.vertx();
        client = collectorClient.createWebClient(vertx, ctx, mongoTest.getMongoPort());
    }

    /**
     * Boots the Mongo database before this test starts.
     * 
     * @throws IOException If no socket was available for the Mongo database.
     */
    @BeforeClass
    public static void setUpMongoDatabase() throws IOException {
        mongoTest = new MongoTest();
        final ServerSocket socket = new ServerSocket(0);
        int mongoPort = socket.getLocalPort();
        socket.close();
        mongoTest.setUpMongoDatabase(mongoPort);
    }

    /**
     * Initializes the environment for each test case with a mock Mongo Database and a Vert.x set up for testing.
     * 
     * @param context The context of the test Vert.x.
     * @throws IOException Fails the test on unexpected exceptions.
     */
    @Before
    public void setUp(final TestContext context) throws IOException {
        deployVerticle(context);

        this.deviceIdentifier = UUID.randomUUID().toString();
        this.measurementIdentifier = String.valueOf(1L);

        this.form = MultipartForm.create();
        form.attribute(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
        form.attribute(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
        form.attribute(FormAttributes.DEVICE_TYPE.getValue(), "HTC Desire");
        form.attribute(FormAttributes.OS_VERSION.getValue(), "4.4.4");
        form.attribute(FormAttributes.APPLICATION_VERSION.getValue(), "4.0.0-alpha1");
        form.attribute(FormAttributes.LENGTH.getValue(), "200.0");
        form.attribute(FormAttributes.LOCATION_COUNT.getValue(), "10");
        form.attribute(FormAttributes.START_LOCATION.getValue(), "lat: 10.0 lon: 10.0, timestamp: 10000");
        form.attribute(FormAttributes.END_LOCATION.getValue(), "lat: 12.0 lon: 12.0, timestamp: 12000");
    }

    /**
     * Stops the test <code>Vertx</code> instance.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @After
    public void stopVertx(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterClass
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests uploading a file to the Vertx API.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     * @throws Exception Fails the test if anything unexpected happens.
     */
    @Test
    public void testPostFile(final TestContext context) throws Exception {
        uploadAndCheckForSuccess(context, "/test.bin");
    }

    /**
     * Tests that uploading a larger file works as expected.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void testPostLargeFile(final TestContext context) {
        uploadAndCheckForSuccess(context, "/iphone-neu.ccyf");
    }

    /**
     * Tests that trying to upload something using wrong credentials returns HTTP status code 401 as expected.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void uploadWithWrongCredentials_Returns401(final TestContext context) {
        final Async async = context.async();

        final HttpRequest<Buffer> builder = client.post(collectorClient.getPort(), "localhost",
                MEASUREMENTS_UPLOAD_ENDPOINT_PATH);
        builder.sendMultipartForm(form, ar -> {
            if (ar.succeeded()) {
                context.assertEquals(401, ar.result().statusCode());
                context.assertTrue(ar.succeeded());
            } else {
                context.fail(ar.cause());
            }
            async.complete();
        });

        async.await(3_000L);
    }

    /**
     * Tests that an upload with unparseable meta data returns a 422 error.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void testUploadWithUnparseableMetaData_Returns422(final TestContext context) {
        final Async async = context.async();

        // Set invalid value for a form attribute
        this.form = MultipartForm.create();
        form.attribute(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
        form.attribute(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
        form.attribute(FormAttributes.DEVICE_TYPE.getValue(), "HTC Desire");
        form.attribute(FormAttributes.OS_VERSION.getValue(), "4.4.4");
        form.attribute(FormAttributes.APPLICATION_VERSION.getValue(), "4.0.0-alpha1");
        form.attribute(FormAttributes.LENGTH.getValue(), "Sir! You are being hacked!");
        form.attribute(FormAttributes.LOCATION_COUNT.getValue(), "10");
        form.attribute(FormAttributes.START_LOCATION.getValue(), "lat: 10.0 lon: 10.0, timestamp: 10000");
        form.attribute(FormAttributes.END_LOCATION.getValue(), "lat: 12.0 lon: 12.0, timestamp: 12000");

        // Execute
        upload(context, "/test.bin", ar -> {
            if (ar.succeeded()) {
                context.assertEquals(422, ar.result().statusCode());
            } else {
                context.fail(ar.cause());
            }
            async.complete();
        }, result -> context.fail("No file should be saved in case of this error!"),
                result -> context.fail("No file should be saved in case of this error!"));

        async.await(3_000L);
    }

    /**
     * Uploads a file identified by a test resource location and checks that it was
     * uploaded successfully.
     * 
     * @param context The Vert.x test context to use to upload the
     *            file.
     * @param testFileResourceName A resource name of a file to upload for testing.
     */
    private void uploadAndCheckForSuccess(final TestContext context, final String testFileResourceName) {
        final Async async = context.async();

        final Future<Void> returnedRequestFuture = Future.future();
        final Future<Void> measurementSavedFuture = Future.future();
        CompositeFuture.all(returnedRequestFuture, measurementSavedFuture).setHandler(result -> {
            if (result.succeeded()) {
                async.complete();
            } else {
                context.fail("Unable to store measurement or send response!");
            }
        });

        upload(context, testFileResourceName, ar -> {
            if (ar.succeeded()) {
                context.assertEquals(201, ar.result().statusCode());
                context.assertTrue(ar.succeeded());
                context.assertNull(ar.cause());
            } else {
                context.fail(ar.cause());
            }
            returnedRequestFuture.complete();
        }, message -> {
            measurementSavedFuture.complete();
        }, message -> {
            context.fail("Unable to save measurement " + message.body());
            measurementSavedFuture.complete();
        });

        async.await(5_000L);
    }

    /**
     * Uploads the provided file using an authenticated request. You may listen to the completion of this upload using
     * any of the provided handlers.
     * 
     * @param context The test context to use.
     * @param testFileResourceName The Java resource name of a file to upload.
     * @param handler The handler called if the client received a response.
     * @param measurementSavedHandler The handler called if the backend has saved the file successfully.
     * @param measurementSavingFailedHandler The handler called if the file was not saved.
     */
    private void upload(final TestContext context, final String testFileResourceName,
            final Handler<AsyncResult<HttpResponse<Buffer>>> handler,
            final Handler<Message<Object>> measurementSavedHandler,
            final Handler<Message<Object>> measurementSavingFailedHandler) {

        final URL testFileResource = this.getClass().getResource(testFileResourceName);

        form.binaryFileUpload("fileToUpload",
                String.format(Locale.US, "%s_%s.cyf", deviceIdentifier, measurementIdentifier),
                testFileResource.getFile(), "application/octet-stream");

        final EventBus eventBus = vertx.eventBus();
        eventBus.consumer(EventBusAddresses.MEASUREMENT_SAVED, measurementSavedHandler);
        eventBus.consumer(EventBusAddresses.SAVING_MEASUREMENT_FAILED, measurementSavingFailedHandler);

        LOGGER.debug("Sending authentication request!");
        TestUtils.authenticate(client, authResponse -> {
            if (authResponse.succeeded()) {
                context.assertEquals(authResponse.result().statusCode(), 200);
                final String authToken = authResponse.result().getHeader("Authorization");
                context.assertNotNull(authToken);

                final HttpRequest<Buffer> builder = client.post(collectorClient.getPort(), "localhost",
                        MEASUREMENTS_UPLOAD_ENDPOINT_PATH);
                builder.putHeader("Authorization", "Bearer " + authToken);
                builder.sendMultipartForm(form, handler);
            } else {
                context.fail(authResponse.cause());
            }
        }, collectorClient.getPort());
    }
}
