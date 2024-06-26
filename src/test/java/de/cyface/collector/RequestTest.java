/*
 * Copyright 2018-2024 Cyface GmbH
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

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.auth.MockedHandlerBuilder;
import de.cyface.collector.commons.DataCollectorClient;
import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * This tests the REST-API provided by the collector and used to upload the data to the server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
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
     * Deploys the {@link CollectorApiVerticle} in a test context.
     *
     * @param vertx A <code>Vertx</code> instance used for deploying the verticle
     * @param ctx The test context used to control the test <code>Vertx</code>
     */
    @BeforeEach
    public void deployVerticle(final Vertx vertx, final VertxTestContext ctx) {
        collectorClient = new DataCollectorClient();
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase();
        client = collectorClient.createWebClient(vertx, ctx, mongoTest, new MockedHandlerBuilder());
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterEach // We need a new database for each test execution or else data remains in the database.
    void shutdown() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests the correct workings of accessing the API specification.
     *
     * @param context The test context for running <code>Vertx</code> under test
     */
    @MethodSource("testParameters")
    @ParameterizedTest
    public void testGetRoot_returnsApiSpecification(final String apiEndpoint, final VertxTestContext context) {
        client.get(collectorClient.getPort(), TEST_HOST, apiEndpoint)
                .send(context.succeeding(response -> context.verify(() -> {
                    assertThat("Invalid HTTP status code on request for API specification.", response.statusCode(),
                            is(200));
                    final var body = response.bodyAsString();
                    final var expectedContent = "<title>Cyface Data Collector</title>";
                    final var assertBodyReason = "Request for API specification seems to be missing a valid body.";
                    assertThat(assertBodyReason, body, containsString(expectedContent));
                    context.completeNow();
                })));
    }

    /**
     * Tests that the default error handler correctly returns 404 status as response for a non-valid request.
     *
     * @param ctx The test context for running <code>Vertx</code> under test
     */
    @MethodSource("testParameters")
    @ParameterizedTest
    public void testGetUnknownResource_Returns404(final String apiEndpoint, final VertxTestContext ctx) {
        client.post(collectorClient.getPort(), TEST_HOST, apiEndpoint + "garbage")
                .send(ctx.succeeding(response -> ctx.verify(() -> {
                    assertThat("Invalid HTTP status code on requesting invalid resource!", response.statusCode(),
                            is(404));
                    ctx.completeNow();
                })));
    }

    /**
     * Parameters used in tests above - see @MethodSource("testParameters").
     *
     * @return Provide the parameters for each run of the parameterized test
     */
    static Stream<String> testParameters() {
        return Stream.of("/");
    }
}
