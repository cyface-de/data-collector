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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

/**
 * This tests the REST-API provided by the collector and used to upload the data to the server.
 * 
 * @author Klemens Muthmann
 * @version 2.3.0
 * @since 1.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class RequestTest {

    /**
     * The logger used for objects of this class. Configure it by either changing values in
     * <code>src/main/resources/logback.xml</code> or in <code>src/test/resources/logback-test.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTest.class);
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
     * Boots the Mongo database before this test starts.
     * 
     * @throws IOException If no socket was available for the Mongo database.
     */
    @BeforeClass
    public static void setupMongoDatabase() throws IOException {
        mongoTest = new MongoTest();
        final ServerSocket socket = new ServerSocket(0);
        int mongoPort = socket.getLocalPort();
        socket.close();
        mongoTest.setUpMongoDatabase(mongoPort);
    }

    /**
     * Deploys the {@link CollectorApiVerticle} in a test context.
     * 
     * @param ctx The test context used to control the test <code>Vertx</code>.
     * @throws IOException Fails the test if anything unexpected goes wrong.
     */
    @Before
    public void deployVerticle(final TestContext ctx) throws IOException {

        vertx = Vertx.vertx();

        collectorClient = new DataCollectorClient();
        client = collectorClient.createWebClient(vertx, ctx, mongoTest.getMongoPort());
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
     * Tests the correct workings of accessing the API specification.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     * @throws Throwable Fails the test if anything unexpected happens.
     */
    @Test
    public void testGetRoot_returnsApiSpecification(final TestContext context) throws Throwable {
        final Async async = context.async();

        client.get(collectorClient.getPort(), "localhost", "/api/v2/").send(response -> {
            if (response.succeeded()) {
                context.assertEquals(response.result().statusCode(), 200);
                final String body = response.result().bodyAsString();
                context.assertTrue(body.contains("<title>Cyface Data Collector</title>"));
            } else {
                context.fail();
            }
            async.complete();
        });

        async.await(3_000L);

    }

    /**
     * Tests that the default error handler correctly returns 404 status as response for a non valid request.
     * 
     * @param ctx The test context for running <code>Vertx</code> under test.
     * @throws Throwable Fails the test if anything unexpected happens.
     */
    @Test
    public void testGetUnknownResource_Returns404(final TestContext ctx) throws Throwable {
        final Async async = ctx.async();

        client.post(collectorClient.getPort(), "localhost", "/api/v2/garbage").send(response -> {
            ctx.assertEquals(response.result().statusCode(), 404);
            async.complete();
        });
        async.await(3_000L);
    }

    /**
     * Tests that the UI returns 401 if a login is attempted with invalid credentials.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void testLoginWithWrongCredentials_Returns401(final TestContext context) {
        final Async async = context.async();

        client.post(collectorClient.getPort(), "localhost", "/api/v2/login")
                .sendJson(new JsonObject().put("username", "unknown").put("password", "unknown"), result -> {
                    context.assertEquals(result.result().statusCode(), 401);
                    async.complete();
                });

        async.await(3_000L);
    }

    /**
     * Tests that JWT token generation works as expected.
     * 
     * @param context The test context for running <code>Vertx</code> under test.
     */
    @Test
    public void testLogin_HappyPath(final TestContext context) {
        final Async async = context.async();

        client.post(collectorClient.getPort(), "localhost", "/api/v2/login")
                .sendJson(new JsonObject().put("username", "admin").put("password", "secret"), result -> {
                    context.assertEquals(result.result().statusCode(), 200);
                    context.assertTrue(result.result().headers().contains("Authorization"));
                    async.complete();
                });

        async.await(3_000L);
    }
}
