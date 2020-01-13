/*
 * Copyright 2018, 2019 Cyface GmbH
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

import java.io.IOException;
import java.net.ServerSocket;

import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;

/**
 * A client providing capabilities for tests to communicate with a Cyface Data Collector server.
 *
 * @author Klemens Muthmann
 * @version 1.1.2
 * @since 2.0.0
 */
final class DataCollectorClient {

    /**
     * The port the server is reachable at.
     */
    private transient int port;

    /**
     * @return The port the server is reachable at.
     */
    int getPort() {
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
    WebClient createWebClient(final Vertx vertx, final TestContext ctx, final int mongoPort) throws IOException {
        try (ServerSocket socket = new ServerSocket(0);) {
            port = socket.getLocalPort();

            final JsonObject mongoDbConfig = new JsonObject()
                    .put("connection_string", "mongodb://localhost:" + mongoPort)
                    .put("db_name", "cyface");

            final JsonObject config = new JsonObject().put(Parameter.MONGO_DATA_DB.key(), mongoDbConfig)
                    .put(Parameter.MONGO_USER_DB.key(), mongoDbConfig).put(Parameter.COLLECTOR_HTTP_PORT.key(), port)
                    .put(Parameter.JWT_PRIVATE_KEY_FILE_PATH.key(),
                            this.getClass().getResource("/private_key.pem").getFile())
                    .put(Parameter.JWT_PUBLIC_KEY_FILE_PATH.key(), this.getClass().getResource("/public.pem").getFile())
                    .put(Parameter.COLLECTOR_HOST.key(), "localhost")
                    .put(Parameter.COLLECTOR_ENDPOINT.key(), "/api/v2/");
            final DeploymentOptions options = new DeploymentOptions().setConfig(config);

            vertx.deployVerticle(CollectorApiVerticle.class.getName(), options, ctx.asyncAssertSuccess());

            return WebClient.create(vertx);
        }
    }
}
