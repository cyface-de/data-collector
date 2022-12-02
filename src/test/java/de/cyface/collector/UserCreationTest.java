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
package de.cyface.collector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cyface.api.EndpointConfig;
import de.cyface.api.Parameter;
import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.verticle.ManagementApiVerticle;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests whether user creation via the management API works as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.6
 * @since 2.0.0
 */
@ExtendWith(VertxExtension.class)
@SuppressWarnings("PMD.MethodNamingConventions")
public final class UserCreationTest {
    /**
     * A {@link MongoTest} instance used to start and stop an in memory Mongo database.
     */
    private static MongoTest mongoTest;
    /**
     * The port the management API under test runs at. The test tries to find a free port by itself as part of its set
     * up.
     */
    private int port;
    /**
     * The <code>WebClient</code> to simulate client requests.
     */
    private WebClient client;
    /**
     * The configuration for the simulated Mongo user database.
     */
    private JsonObject mongoDbConfiguration;

    /**
     * Boots the Mongo database before this test starts.
     *
     * @throws IOException If no socket was available for the Mongo database.
     */
    @BeforeAll
    public static void setUpMongoDatabase() throws IOException {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase(Network.freeServerPort(Network.getLocalHost()));
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterAll
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    /**
     * Deploys all required verticles. Also provides a
     * <code>WebClient</code> to simulate client requests.
     *
     * @param vertx A <code>Vertx</code> instance injected for this test to use
     * @param context The Vert.x test context used to control the test process
     * @throws IOException If unable to open a socket for the test HTTP server
     */
    @SuppressWarnings("JUnitMalformedDeclaration")
    @BeforeEach
    public void setUp(final Vertx vertx, final VertxTestContext context) throws IOException {

        port = Network.freeServerPort(Network.getLocalHost());

        mongoDbConfiguration = new JsonObject()
                .put("connection_string", "mongodb://localhost:" + mongoTest.getMongoPort())
                .put("db_name", "cyface");

        final var config = new JsonObject().put(Parameter.MANAGEMENT_HTTP_PORT.key(), port)
                .put(Parameter.MONGO_DB.key(), mongoDbConfiguration);
        final var options = new DeploymentOptions().setConfig(config);

        final var managementApiVerticle = new ManagementApiVerticle("test-salt");
        vertx.deployVerticle(managementApiVerticle, options, context.succeedingThenComplete());

        client = WebClient.create(vertx);
    }

    /**
     * Closes the <code>vertx</code> instance.
     *
     * @param vertx A <code>Vertx</code> instance injected for this test to use
     * @param context The Vert.x test context used to control the test process
     */
    @SuppressWarnings("JUnitMalformedDeclaration")
    @AfterEach
    public void tearDown(final Vertx vertx, final VertxTestContext context) {

        // Delete entries so that the next tests are independent
        final MongoClient mongoClient = EndpointConfig.createSharedMongoClient(vertx, mongoDbConfiguration);
        mongoClient.removeDocuments("user", new JsonObject(), context.succeedingThenComplete());
    }

    /**
     * Tests that the normal process of creating a test user via the management interface works as expected.
     *
     * @param vertx A <code>Vertx</code> instance injected for this test to use
     * @param context The Vert.x test context used to control the test process
     */
    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    public void testCreateUser_HappyPath(final Vertx vertx, final VertxTestContext context) {
        // Arrange
        final var postUserCompleteCheckpoint = context.checkpoint();
        final var checkedIfUserIsInDataBase = context.checkpoint();
        final var checkedThatCorrectUserIsInDatabase = context.checkpoint();
        final var mongoClient = EndpointConfig.createSharedMongoClient(vertx, mongoDbConfiguration);

        // Act
        final var requestPostedFuture = client.post(port, "localhost", "/user").sendJsonObject(
                new JsonObject().put("username", "test-user").put("password", "test-password").put("role",
                        "testGroup_user"));
        requestPostedFuture.onComplete(context.succeeding(result -> postUserCompleteCheckpoint.flag()));

        final var countResultsFuture = requestPostedFuture.compose(response -> {
            context.verify(() -> assertThat("Invalid HTTP status code on user insertion request!",
                    response.statusCode(), is(201)));
            return mongoClient.count("user", new JsonObject());
        });
        countResultsFuture.onComplete(context.succeeding(result -> context.verify(() -> {
            assertThat("Database does not contain exactly one entry after inserting a user!", result, is(1L));
            checkedIfUserIsInDataBase.flag();
        })));

        final var checkUsernameFuture = countResultsFuture
                .compose(res -> mongoClient.findOne("user", new JsonObject(), null));
        checkUsernameFuture.onComplete(context.succeeding(result -> context.verify(() -> {
            assertThat("Unable to load correct user from database!", result.getString("username"), is("test-user"));
            checkedThatCorrectUserIsInDatabase.flag();
        })));
    }
}
