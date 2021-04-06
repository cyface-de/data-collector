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
    JsonObject config() throws IOException {

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
