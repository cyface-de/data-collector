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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.ServerSocket;

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
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * This tests the REST-API provided by the collector and used to upload the data to the server.
 *
 * @author Klemens Muthmann
 * @version 2.3.2
 * @since 1.0.0
 */
@ExtendWith(VertxExtension.class)
@SuppressWarnings("PMD.MethodNamingConventions")
public final class RequestTest {

    /**
     * The logger used for objects of this class. Configure it by either changing values in
     * <code>src/main/resources/logback.xml</code> or in <code>src/test/resources/logback-test.xml</code>.
     */
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTest.class);
    /**
     * The hostname used to send test requests to.
     */
    private static final String TEST_HOST = "localhost";
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
     * @throws IOException If no socket was available for the Mongo database
     */
    @BeforeAll
    public static void setupMongoDatabase() throws IOException {
        mongoTest = new MongoTest();
        try (ServerSocket socket = new ServerSocket(0)) {
            final int mongoPort = socket.getLocalPort();
            socket.close();
            mongoTest.setUpMongoDatabase(mongoPort);
        }
    }

    /**
     * Deploys the {@link CollectorApiVerticle} in a test context.
     *
     * @param vertx A <code>Vertx</code> instance used for deploying the verticle
     * @param ctx The test context used to control the test <code>Vertx</code>
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    @BeforeEach
    public void deployVerticle(final Vertx vertx, final VertxTestContext ctx) throws IOException {
        collectorClient = new DataCollectorClient();
        client = collectorClient.createWebClient(vertx, ctx, mongoTest.getMongoPort());
    }
    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterAll
    public static void stopMongoDatabase() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests the correct workings of accessing the API specification.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testGetRoot_returnsApiSpecification(final VertxTestContext context) {
        client.get(collectorClient.getPort(), TEST_HOST, "/api/v2/")
                .send(context.succeeding(response -> context.verify(() -> {
                    assertThat("Invalid HTTP status code on request for API specification.", response.statusCode(), is(200));
                    final var body = response.bodyAsString();
                    final var expectedContent = "<title>Cyface Data Collector</title>";
                    final var assertBodyReason = "Request for API specification seems to be missing a valid body.";
                    assertThat(assertBodyReason, body, containsString(expectedContent));
                    context.completeNow();
                })));
    }

    /**
     * Tests that the default error handler correctly returns 404 status as response for a non valid request.
     *
     * @param ctx The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testGetUnknownResource_Returns404(final VertxTestContext ctx) {
        client.post(collectorClient.getPort(), TEST_HOST, "/api/v2/garbage")
                .send(ctx.succeeding(response -> ctx.verify(() -> {
                    assertThat("Invalid HTTP status code on requesting invalid resource!", response.statusCode(),
                            is(404));
                    ctx.completeNow();
                })));
    }

    /**
     * Tests that the UI returns 401 if a login is attempted with invalid credentials.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testLoginWithWrongCredentials_Returns401(final VertxTestContext context) {
        client.post(collectorClient.getPort(), TEST_HOST, "/api/v2/login")
                .sendJson(new JsonObject().put("username", "unknown").put("password", "unknown"),
                        context.succeeding(result -> context.verify(() -> {
                            assertThat("Invalid HTTP status code on invalid login request!", result.statusCode(),
                                    is(401));
                            context.completeNow();
                        })));
    }

    /**
     * Tests that JWT token generation works as expected.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @Test
    public void testLogin_HappyPath(final VertxTestContext context) {
        client.post(collectorClient.getPort(), TEST_HOST, "/api/v2/login")
                .sendJson(new JsonObject().put("username", "admin").put("password", "secret"),
                        context.succeeding(result -> context.verify(() -> {
                            assertThat("Invalid HTTP status code on login request!", result.statusCode(), is(200));
                            assertThat("Login request contained no JWT token!",
                                    result.headers().contains("Authorization"), is(true));
                            context.completeNow();
                        })));
    }
}
