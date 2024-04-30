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
package de.cyface.collector.commons;

import java.io.IOException;

import de.cyface.collector.auth.AuthHandlerBuilder;
import de.cyface.collector.verticle.CollectorApiVerticle;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;

/**
 * A client providing capabilities for tests to communicate with a Cyface Data Collector server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
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
     * @param mongoClient A representation of the in-memory test database.
     * @return A completely configured <code>WebClient</code> capable of accessing the started Cyface Data Collector.
     * @throws IOException If the server port could not be opened.
     */
    public WebClient createWebClient(
            final Vertx vertx,
            final VertxTestContext ctx,
            final MongoTest mongoClient,
            final AuthHandlerBuilder authHandlerBuilder
            )
            throws IOException {
        port = Network.freeServerPort(Network.getLocalHost());

        final var mongoDbConfig = mongoClient.clientConfiguration();

        final var config = ConfigurationFactory.INSTANCE.mockedConfiguration(
                port,
                mongoDbConfig,
                measurementLimit);

        final var collectorVerticle = new CollectorApiVerticle(
                authHandlerBuilder,
                config.getHttpPort(),
                config.getMeasurementPayloadLimit(),
                config.getUploadExpiration(),
                config.getStorageType(),
                config.getMongoDb()
            );
        vertx.deployVerticle(collectorVerticle, ctx.succeedingThenComplete());

        return WebClient.create(vertx);
    }
}
