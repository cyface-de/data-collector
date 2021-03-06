/*
 * Copyright 2021 Cyface GmbH
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

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cyface.collector.commons.MongoTest;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests that starting the {@link MainVerticle} works as expected.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 5.2.1
 */
@ExtendWith(VertxExtension.class)
public class MainVerticleTest {

    /**
     * Process providing a connection to the test Mongo database.
     */
    private transient MongoTest mongoTest;

    /**
     * The object of the class under test. This instance should be reinitialized on each test run.
     */
    private MainVerticle oocut;

    /**
     * Starts a test in memory Mongo database.
     *
     * @throws IOException If creating the server fails
     */
    @BeforeEach
    void setUp() throws IOException {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase(Network.getFreeServerPort());
        oocut = new MainVerticle();
    }

    /**
     * Stops the Mongo database.
     */
    @AfterEach
    void shutdown() {
        mongoTest.stopMongoDb();
    }

    /**
     * Creates a valid startup configuration to be used by tests.
     * 
     * @return The pre filled startup configuration
     * @throws IOException If no valid server port was available
     */
    private JsonObject config() throws IOException {

        //noinspection SpellCheckingInspection
        return new JsonObject()
                .put("mongo.userdb", mongoTest.clientConfiguration())
                .put("jwt.public", this.getClass().getResource("/public.pem").getFile())
                .put("jwt.private", this.getClass().getResource("/private_key.pem").getFile())
                .put("http.host", "localhost")
                .put("http.endpoint", "/api/v2/")
                .put("http.port", Network.getFreeServerPort())
                .put("salt", "abcdefg")
                .put("mongo.datadb", mongoTest.clientConfiguration());
    }

    /**
     * Tests that the startup of the {@link MainVerticle} fails if both "salt" and "salt.path" parameters are supplied.
     *
     * @param vertx The Vertx instance used for testing
     * @param testContext The Vertx test context used to control test execution
     * @throws IOException If no free server port could be generated
     */
    @Test
    @DisplayName("Fail startup if salt and salt.path are present!")
    void test(final Vertx vertx, final VertxTestContext testContext) throws IOException {
        final var config = config();
        config.put("salt.path", this.getClass().getResource("/salt.file").getFile());
        final var deploymentOptions = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(oocut, deploymentOptions, result -> {
            if (result.succeeded()) {
                testContext.failNow(result.cause());
            } else {
                testContext.completeNow();
            }
        });
    }

    /**
     * Tests that startup of the {@link MainVerticle} works if a correct parameter set has been supplied.
     *
     * @param vertx The Vertx instance used for testing
     * @param testContext The Vertx test context used to control test execution
     * @throws IOException If no free server port could be generated
     */
    @Test
    @DisplayName("Successful startup happy path!")
    void testHappyPath(final Vertx vertx, final VertxTestContext testContext) throws IOException {
        final var deploymentOptions = new DeploymentOptions().setConfig(config());
        vertx.deployVerticle(oocut, deploymentOptions, result -> {
            if (result.succeeded()) {
                testContext.completeNow();
            } else {
                testContext.failNow(result.cause());
            }
        });
    }
}
