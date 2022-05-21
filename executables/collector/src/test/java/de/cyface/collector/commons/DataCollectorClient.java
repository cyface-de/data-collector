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
package de.cyface.collector.commons;

import java.io.IOException;

import org.apache.commons.lang3.Validate;

import de.cyface.api.Parameter;
import de.cyface.collector.verticle.CollectorApiVerticle;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;

/**
 * A client providing capabilities for tests to communicate with a Cyface Data Collector server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.3.0
 * @since 2.0.0
 */
public final class DataCollectorClient {

    /**
     * The port the server is reachable at.
     */
    private transient int port;
    /**
     * The maximal number of {@code Byte}s which may be uploaded.
     */
    private Long measurementLimit;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param measurementLimit The maximal number of {@code Byte}s which may be uploaded.
     */
    public DataCollectorClient(long measurementLimit) {
        this.measurementLimit = measurementLimit;
    }

    /**
     * Creates a fully initialized instance of this class.
     */
    public DataCollectorClient() {
    }

    /**
     * @return The port the server is reachable at.
     */
    public int getPort() {
        return port;
    }

    /**
     * Starts a test Cyface Data Collector and creates a Vert.x <code>WebClient</code> usable to access a Cyface Data
     * Collector.
     *
     * @param vertx The <code>Vertx</code> instance to start and access the server.
     * @param ctx The <code>TestContext</code> to create a new Server and <code>WebClient</code>.
     * @param mongoPort The port to run the test Mongo database under.
     * @return A completely configured <code>WebClient</code> capable of accessing the started Cyface Data Collector.
     * @throws IOException If the server port could not be opened.
     */
    public WebClient createWebClient(final Vertx vertx, final VertxTestContext ctx, final int mongoPort)
            throws IOException {
        port = Network.getFreeServerPort();

        final var mongoDbConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:" + mongoPort)
                .put("db_name", "cyface");

        final var privateKey = this.getClass().getResource("/private_key.pem");
        final var publicKey = this.getClass().getResource("/public.pem");
        Validate.notNull(privateKey);
        Validate.notNull(publicKey);
        final var config = new JsonObject().put(Parameter.MONGO_DB.key(), mongoDbConfig)
                // Databases for API v2
                .put(de.cyface.api.v2.Parameter.MONGO_DATA_DB.key(), mongoDbConfig)
                .put(de.cyface.api.v2.Parameter.MONGO_USER_DB.key(), mongoDbConfig)
                .put(Parameter.HTTP_PORT.key(), port)
                .put(Parameter.JWT_PRIVATE_KEY_FILE_PATH.key(), privateKey.getFile())
                .put(Parameter.JWT_PUBLIC_KEY_FILE_PATH.key(), publicKey.getFile())
                .put(Parameter.HTTP_HOST.key(), "localhost")
                .put(Parameter.HTTP_ENDPOINT_V3.key(), "/api/v3/")
                .put(Parameter.HTTP_ENDPOINT_V2.key(), "/api/v2/");
        if (measurementLimit != null) {
            config.put(Parameter.MEASUREMENT_PAYLOAD_LIMIT.key(), measurementLimit);
        }
        final var options = new DeploymentOptions().setConfig(config);

        final var collectorVerticle = new CollectorApiVerticle("test-salt");
        vertx.deployVerticle(collectorVerticle, options, ctx.succeedingThenComplete());

        return WebClient.create(vertx);
    }
}
