package de.cyface.collector.verticle;

import de.cyface.collector.commons.MongoTest;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

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
     * @throws IOException If creating the server fails
     */
    @BeforeEach
    void setUp() throws IOException {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase(Network.getFreeServerPort());
    }

    /**
     * Stops the Mongo database.
     */
    @AfterEach
    void shutdown() {
        mongoTest.stopMongoDb();
    }

    /**
     * Runs a happy path test for starting the CollectorApiVerticle.
     *
     * @param vertx The test Vertx instance to use
     * @param testContext A test context to handle Vertx asynchronity
     */
    @Test
    @DisplayName("Happy Path test for starting the collector API.")
    void test(final Vertx vertx, final VertxTestContext testContext) {
        // Arrange

        final var configuration = new JsonObject()
                .put("jwt.private", this.getClass().getResource("/private_key.pem").getFile())
                .put("jwt.public", this.getClass().getResource("/public.pem").getFile())
                .put("http.host", "localhost")
                .put("http.endpoint", "/api/v2")
                .put("mongo.datadb", new JsonObject()
                        .put("db_name", "cyface-data")
                        .put("connection_string","mongodb://localhost:27019")
                        .put("data_source_name","cyface-data"))
                .put("mongo.userdb", mongoTest.clientConfiguration())
                .put("jwt.expiration", 3600);

        final var deploymentOptions = new DeploymentOptions();
        deploymentOptions.setConfig(configuration);

        // Act, Assert
        vertx.deployVerticle(new CollectorApiVerticle(), deploymentOptions, testContext.succeedingThenComplete());
    }
}
