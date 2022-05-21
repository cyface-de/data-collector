/*
 * Copyright 2020-2021 Cyface GmbH
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
package de.cyface.api.v2;

import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Configuration parameters required to start the HTTP server which also handles routing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public interface EndpointConfig {

    /**
     * The port on which the HTTP server should listen if no port was specified.
     */
    int DEFAULT_HTTP_PORT = 8080;
    /**
     * The default data source name to use for user and data database if none is provided via configuration.
     */
    String DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface";

    /**
     * Creates a shared Mongo database client for the provided configuration.
     *
     * @param vertx The <code>Vertx</code> instance to create the client from.
     * @param config Configuration of the newly created client. For further information refer to
     *            {@link Parameter#MONGO_DATA_DB} and {@link Parameter#MONGO_USER_DB}.
     * @return A <code>MongoClient</code> ready for usage.
     */
    static MongoClient createSharedMongoClient(final Vertx vertx, final JsonObject config) {
        Objects.requireNonNull(config, String.format(
                "Unable to load Mongo database configuration. "
                        + "Please provide a valid configuration using the %s and/or %s parameter and at least as \"db_name\", "
                        + "a \"connection_string\" and a \"data_source_name\"! Also check if your database is running "
                        + "and accessible!",
                Parameter.MONGO_DATA_DB.key(), Parameter.MONGO_USER_DB.key()));
        final var dataSourceName = config.getString("data_source_name", DEFAULT_MONGO_DATA_SOURCE_NAME);
        return MongoClient.createShared(vertx, config, dataSourceName);
    }

    int getHttpPort();

    String getHost();

    String getEndpoint();
}