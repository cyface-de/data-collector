/*
 * Copyright 2021-2023 Cyface GmbH
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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import de.cyface.collector.auth.MockedHandlerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.configuration.AuthType;
import de.cyface.collector.configuration.Configuration;
import de.cyface.collector.configuration.GridFsStorageType;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests if running the {@link CollectorApiVerticle} works as expected.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 5.2.0
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
     * @throws IOException If creating the server fails
     */
    @BeforeEach
    void setUp() throws IOException {
        mongoTest = new MongoTest();
        int mongoPort = Network.freeServerPort(Network.getLocalHost());
        mongoTest.setUpMongoDatabase(mongoPort);
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
        final var port = Network.freeServerPort(Network.getLocalHost());
        final var configuration = mock(Configuration.class);
        when(configuration.getHttpPort()).thenReturn(port);
        when(configuration.getMongoDb()).thenReturn(mongoTest.clientConfiguration());
        when(configuration.getStorageType()).thenReturn(new GridFsStorageType(Path.of("upload-folder")));
        when(configuration.getUploadExpiration()).thenReturn(60_000L);
        when(configuration.getMeasurementPayloadLimit()).thenReturn(1_048_576L);
        when(configuration.getAuthType()).thenReturn(AuthType.Mocked);
        when(configuration.getOauthConfig().getCallback()).thenReturn(new URL("http://localhost:8080/callback"));
        when(configuration.getOauthConfig().getClient()).thenReturn("collector-test");
        when(configuration.getOauthConfig().getSecret()).thenReturn("SECRET");
        when(configuration.getOauthConfig().getSite()).thenReturn(new URL("https://example.com:8443/realms/{tenant}"));
        when(configuration.getOauthConfig().getTenant()).thenReturn("rfr");

        // Act, Assert
        vertx.deployVerticle(new CollectorApiVerticle(
                new MockedHandlerBuilder(),
                configuration.getHttpPort(),
                configuration.getMeasurementPayloadLimit(),
                configuration.getUploadExpiration(),
                configuration.getStorageType(),
                configuration.getMongoDb()
        ), testContext.succeedingThenComplete());

        // https://vertx.io/docs/vertx-junit5/java/#_a_test_context_for_asynchronous_executions
        assertThat(testContext.awaitCompletion(20, TimeUnit.SECONDS), is(equalTo(true)));
        if (testContext.failed()) {
            throw testContext.causeOfFailure();
        }
    }
}
