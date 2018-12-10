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

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 * 
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class FileUploadTest {

    /**
     * The test <code>Vertx</code> instance.
     */
    private Vertx vertx;
    /**
     * A Mongo database lifecycle handler. This provides the test with the
     * capabilities to run and shutdown a Mongo database for testing purposes.
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
     * A globally unqiue identifier of the simulated upload device. The actual value
     * does not really matter.
     */
    private String deviceIdentifier = UUID.randomUUID().toString();
    /**
     * The measurement identifier used for the test measurement. The actual value
     * does not matter that much. It simulates a device wide unique identifier.
     */
    private String measurementIdentifier = String.valueOf(1L);

    /**
     * The Vert.x multipart form to upload.
     */
    private MultipartForm form = MultipartForm.create();

    /**
     * Deploys the {@link MainVerticle} in a test context.
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
        ServerSocket socket = new ServerSocket(0);
        int mongoPort = socket.getLocalPort();
        socket.close();
        mongoTest.setUpMongoDatabase(mongoPort);
    }

    /**
     * Initializes the environment for each test case with a mock Mongo Database and
     * a Vert.x set up for testing.
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
        form.attribute("deviceId", deviceIdentifier);
        form.attribute("measurementId", measurementIdentifier);
        form.attribute("deviceType", "HTC Desire");
        form.attribute("osVersion", "4.4.4");
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
     * Uploads a file identified by a test resource location and checks that it was
     * uploaded successfully.
     * 
     * @param context The Vert.x test context to use to upload the
     *            file.
     * @param testFileResourceName A resource name of a file to upload for testing.
     */
    private void uploadAndCheckForSuccess(final TestContext context, final String testFileResourceName) {
        Async async = context.async();
        final URL testFileResource = this.getClass().getResource(testFileResourceName);

        form.binaryFileUpload("fileToUpload",
                String.format(Locale.US, "%s_%s.cyf", deviceIdentifier, measurementIdentifier),
                testFileResource.getFile(), "application/octet-stream");

        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(EventBusAddresses.MEASUREMENT_SAVED, message -> {
            async.complete();
        });
        eventBus.consumer(EventBusAddresses.SAVING_MEASUREMENT_FAILED, message -> {
            context.fail("Unable to save measurement " + message.body());
        });

        TestUtils.authenticate(client, authResponse -> {
            if (authResponse.succeeded()) {
                context.assertEquals(authResponse.result().statusCode(), 200);
                final String authToken = authResponse.result().bodyAsString();

                final HttpRequest<Buffer> builder = client
                        .post(collectorClient.getPort(), "localhost", "/api/v2/measurements").ssl(true);
                builder.putHeader("Authorization", "Bearer " + authToken);
                builder.sendMultipartForm(form, ar -> {
                    if (ar.succeeded()) {
                        context.assertEquals(ar.result().statusCode(), 201);
                        context.assertTrue(ar.succeeded());
                        context.assertNull(ar.cause());
                    } else {
                        context.fail(ar.cause());
                    }
                });
            } else {
                context.fail(authResponse.cause());
            }
        }, collectorClient.getPort());

        async.await(3000L);
    }
}
