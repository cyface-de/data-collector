/*
 * Copyright 2021-2025 Cyface GmbH
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
package de.cyface.collector.verticle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import de.cyface.collector.auth.MockedHandlerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.configuration.Configuration;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests if running the {@link CollectorApiVerticle} works as expected.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension.class)
public class CollectorApiVerticleTest {
    /**
     * Process providing a connection to the test Mongo database.
     */
    private transient MongoTest mongoTest;

    /**
     * Starts a test in memory Mongo database.
     *
     */
    @BeforeEach
    void setUp() {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase();
    }

    /**
     * Stops the Mongo database.
     */
    @AfterEach
    void shutdown() {
        mongoTest.stopMongoDb();
    }

    /**
     * Runs a happy path test for starting the CollectorApiVerticle
     *
     * @param vertx The test Vertx instance to use
     * @param testContext A test context to handle Vertx asynchronicity
     * @throws IOException if no free port could be retrieved
     */
    @Test
    @DisplayName("Happy Path test for starting the collector API.")
    void test(final Vertx vertx, final VertxTestContext testContext) throws Throwable {
        // Arrange
        final var configuration = mock(Configuration.class);

        // Act, Assert
        vertx.deployVerticle(new CollectorApiVerticle(
                new MockedHandlerBuilder(),
                new ServerConfiguration(
                    configuration.getHttpPort(),
                    "/",
                    configuration.getMeasurementPayloadLimit(),
                    configuration.getUploadExpiration()
                ),
                mock()
        ), testContext.succeedingThenComplete());

        // https://vertx.io/docs/vertx-junit5/java/#_a_test_context_for_asynchronous_executions
        assertThat(testContext.awaitCompletion(20, TimeUnit.SECONDS), is(equalTo(true)));
        if (testContext.failed()) {
            throw testContext.causeOfFailure();
        }
    }

    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No free port found", e);
        }
    }
}
