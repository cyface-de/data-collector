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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.Validate;
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
 * @version 1.0.1
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
        mongoTest.setUpMongoDatabase(Network.freeServerPort(Network.getLocalHost()));
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

        final var publicKey = this.getClass().getResource("/public.pem");
        final var privateKey = this.getClass().getResource("/private_key.pem");
        Validate.notNull(publicKey);
        Validate.notNull(privateKey);
        // noinspection SpellCheckingInspection
        return new JsonObject()
                .put("jwt.public", publicKey.getFile())
                .put("jwt.private", privateKey.getFile())
                .put("http.host", "localhost")
                .put("http.endpoint", "/api/v3/")
                .put("http.port", Network.freeServerPort(Network.getLocalHost()))
                .put("salt", "abcdefg")
                .put("mongo.db", mongoTest.clientConfiguration());
    }

    /**
     * Tests that the startup of the {@link MainVerticle} fails if both "salt" and "salt.path" parameters are supplied.
     *
     * @param vertx The Vertx instance used for testing
     * @param testContext The Vertx test context used to control test execution
     * @throws IOException If no free server port could be generated
     */
    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    @DisplayName("Fail startup if salt and salt.path are present!")
    void test(final Vertx vertx, final VertxTestContext testContext) throws Throwable {
        final var config = config();
        final var saltResource = this.getClass().getResource("/salt.file");
        Validate.notNull(saltResource);
        config.put("salt", "cyface-salt");
        config.put("salt.path", saltResource.getFile());
        final var deploymentOptions = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(oocut, deploymentOptions, testContext.failingThenComplete());
    }

    /**
     * Tests that startup of the {@link MainVerticle} works if a correct parameter set has been supplied.
     *
     * @param vertx The Vertx instance used for testing
     * @param testContext The Vertx test context used to control test execution
     * @throws IOException If no free server port could be generated
     */
    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    @DisplayName("Successful startup happy path!")
    void testHappyPath(final Vertx vertx, final VertxTestContext testContext) throws Throwable {
        final var deploymentOptions = new DeploymentOptions().setConfig(config());
        vertx.deployVerticle(oocut, deploymentOptions, testContext.succeedingThenComplete());

        // Throw cause (https://vertx.io/docs/vertx-junit5/java/#_a_test_context_for_asynchronous_executions)
        assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS), is(equalTo(true)));
        if (testContext.failed()) {
            throw testContext.causeOfFailure();
        }
    }
}
