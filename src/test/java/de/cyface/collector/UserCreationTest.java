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

import de.cyface.collector.verticle.ManagementApiVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Tests whether user creation via the management API works as expected.
 * 
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 2.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class UserCreationTest {

    /**
     * A {@link MongoTest} instance used to start and stop an in memory Mongo database.
     */
    private static MongoTest mongoTest;

    /**
     * The port the management API under test runs at. The test trys to find a free port by itself as part of its set
     * up.
     */
    private int port;

    /**
     * The <code>WebClient</code> to simulate client requests.
     */
    private WebClient client;

    /**
     * The <code>Vertx</code> instance used to run a system under test.
     */
    private Vertx vertx;

    /**
     * The configuration for the simulated Mongo user database.
     */
    private JsonObject mongoDbConfiguration;

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
     * Finishes the mongo database after this test has finished.
     */
    @AfterClass
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    /**
     * Initializes the <code>vertx</code> instance and deploys all required verticles. Also provides a
     * <code>WebClient</code> to simulate client requests.
     * 
     * @param context The Vert.x test context used to control the test process.
     * @throws IOException If unable to open a socket for the test HTTP server.
     */
    @Before
    public void setUp(final TestContext context) throws IOException {
        vertx = Vertx.vertx();

        final ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        mongoDbConfiguration = new JsonObject()
                .put("connection_string", "mongodb://localhost:" + mongoTest.getMongoPort()).put("db_name", "cyface");

        final JsonObject config = new JsonObject().put(Parameter.MANAGEMENT_HTTP_PORT.key(), port)
                .put(Parameter.MONGO_USER_DB.key(), mongoDbConfiguration);
        final DeploymentOptions options = new DeploymentOptions().setConfig(config);

        vertx.deployVerticle(ManagementApiVerticle.class.getName(), options, context.asyncAssertSuccess());

        client = WebClient.create(vertx);
    }

    /**
     * Closes the <code>vertx</code> instance.
     */
    @After
    public void tearDown(final TestContext context) {
        // Delete entries so that the next tests are independent
        final MongoClient mongoClient = Utils.createSharedMongoClient(vertx, mongoDbConfiguration);
        final Async mongoQueryAsync = context.async();
        mongoClient.removeDocuments("user", new JsonObject(), result -> {
            context.assertTrue(result.succeeded());
            mongoQueryAsync.complete();
        });
        mongoQueryAsync.await(3_000L);

        vertx.close();
    }

    /**
     * Tests that the normal process of creating a test user via the management interface works as expected.
     * 
     * @param context The Vert.x test context used to control the test process.
     */
    @Test
    public void testCreateUser_HappyPath(final TestContext context) {

        // Act
        final Async async = context.async();
        client.post(port, "localhost", "/user").sendJsonObject(
                new JsonObject().put("username", "test-user").put("password", "test-password"),
                result -> {
                    if (result.succeeded()) {
                        async.complete();
                    } else {
                        async.resolve(Future.failedFuture(result.cause()));
                    }
                });
        async.await(3_000L);

        // Assert
        final MongoClient mongoClient = Utils.createSharedMongoClient(vertx, mongoDbConfiguration);

        final Async mongoQueryCountAsync = context.async();
        mongoClient.count("user", new JsonObject(), result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(result.result(), 1L);
            mongoQueryCountAsync.complete();
        });
        mongoQueryCountAsync.await(3_000L);

        final Async mongoQueryAsync = context.async();
        mongoClient.findOne("user", new JsonObject(), null, result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(result.result().getString("username"), "test-user");
            mongoQueryAsync.complete();
        });
        mongoQueryAsync.await(3_000L);
    }

    /**
     * Tests that the normal process of creating multiple test users with one command via the management interface works
     * as expected.
     *
     * @param context The Vert.x test context used to control the test process.
     */
    @Test
    public void testCreateMultipleUsers_HappyPath(final TestContext context) {

        // Act
        final JsonObject payload = new JsonObject().put("usernamePrefix", "test-user").put("numberOfUsers", 2);
        final Async async = context.async();
        client
                .post(port, "localhost", "/users")
                .sendJsonObject(
                        payload,
                        result -> {
                            if (result.succeeded()) {
                                HttpResponse<Buffer> response = result.result();
                                final String credentialHeader = response.getHeader("credentials");
                                context.assertTrue(credentialHeader.contains("{\"test-user1\":\""));
                                context.assertTrue(credentialHeader.contains("\"test-user2\":\""));
                                async.complete();
                            } else {
                                async.resolve(Future.failedFuture(result.cause()));
                            }
                        });
        async.await(3_000L);

        // Assert
        final MongoClient mongoClient = Utils.createSharedMongoClient(vertx, mongoDbConfiguration);

        final Async mongoQueryCountAsync = context.async();
        mongoClient.count("user", new JsonObject(), result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(result.result(), 2L);
            mongoQueryCountAsync.complete();
        });
        mongoQueryCountAsync.await(3_000L);

        final Async mongoQueryCountAsync2 = context.async();
        mongoClient.count("user", new JsonObject().put("username", "test-user1"), result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(result.result(), 1L);
            mongoQueryCountAsync2.complete();
        });
        mongoQueryCountAsync2.await(3_000L);

        final Async mongoQueryCountAsync3 = context.async();
        mongoClient.count("user", new JsonObject().put("username", "test-user2"), result -> {
            context.assertTrue(result.succeeded());
            context.assertEquals(result.result(), 1L);
            mongoQueryCountAsync3.complete();
        });
        mongoQueryCountAsync3.await(3_000L);
    }
}
