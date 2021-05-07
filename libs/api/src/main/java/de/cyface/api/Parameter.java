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
package de.cyface.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An enumeration of parameters, that may be provided upon application startup, to configure the application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 5.3.0
 */
public enum Parameter {

    /**
     * The location of the PEM file containing the private key to issue new JWT tokens.
     */
    JWT_PRIVATE_KEY_FILE_PATH("jwt.private"),
    /**
     * The location of the PEM file containing the public key to check JWT tokens for validity.
     */
    JWT_PUBLIC_KEY_FILE_PATH("jwt.public"),
    /**
     * A parameter setting the hostname the API service is available at.
     */
    HTTP_HOST("http.host"),
    /**
     * A parameter for the endpoint path the API service is available at.
     */
    HTTP_ENDPOINT("http.endpoint"),
    /**
     * The server port the API shall be available at.
     */
    HTTP_PORT("http.port"),
    /**
     * Detailed connection information about the Mongo user database. This database stores all the credentials of users
     * capable of logging in to the systems. This should be a JSON object with supported parameters explained at:
     * <a href= "https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client">
     * https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client</a>.
     */
    @SuppressWarnings("SpellCheckingInspection")
    MONGO_USER_DB("mongo.userdb"),
    /**
     * The username of a default administration user created on system start.
     */
    ADMIN_USER_NAME("admin.user"),
    /**
     * The password for the default administration user created on system start.
     */
    ADMIN_PASSWORD("admin.password"),
    /**
     * Salt used by the Mongo authentication provider to encrypt all user passwords.
     */
    SALT("salt"),
    /**
     * Path to a file containing the salt used to encrypt passwords in the database.
     */
    SALT_PATH("salt.path"),
    /**
     * Detailed connection information about the Mongo data database. This database stores all data received via the
     * REST-API. This should be a JSON object with supported parameters explained at:
     * <a href= "https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client">
     * https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client</a>.
     */
    @SuppressWarnings("SpellCheckingInspection")
    MONGO_DATA_DB("mongo.datadb"),
    /**
     * A parameter telling the system how long a new JWT token stays valid in seconds.
     */
    TOKEN_EXPIRATION_TIME("jwt.expiration"),
    /**
     * The server port the management interface for the API shall be available at.
     */
    MANAGEMENT_HTTP_PORT("http.port.management"),
    /**
     * A parameter telling the system, whether it should publish metrics using Micrometer to Prometheus or not.
     */
    METRICS_ENABLED("metrics.enabled");

    /**
     * The logger used for objects of this class. You can change its configuration by changing the values in
     * <code>src/main/resources/vertx-default-jul-logging.properties</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Parameter.class);

    /**
     * The parameter key used to load its value from the JSON configuration.
     */
    private final String key;

    /**
     * Creates a new completely initialized parameter.
     *
     * @param key The parameter key used to load its value from the JSON configuration.
     */
    Parameter(final String key) {
        this.key = key;
    }

    /**
     * @return The parameter key used to load its value from the JSON configuration.
     */
    public String key() {
        return key;
    }

    /**
     * Provides the string value of this parameter from the Vert.x configuration or the <code>defaultValue</code> if
     * there was none.
     *
     * @param vertx The <code>Vertx</code> instance containing the configuration.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as a <code>String</code> or the <code>defaultValue</code>.
     */
    public String stringValue(final Vertx vertx, final String defaultValue) {
        final var value = vertx.getOrCreateContext().config().getString(key);
        final var ret = value == null ? defaultValue : value;
        LOGGER.info("Using configuration value: {} for key: {}.", ret, key);
        return ret;
    }

    /**
     * Provides the string value of this parameter from the Vert.x configuration or the <code>null</code> if there was
     * none.
     *
     * @param vertx The <code>Vertx</code> instance containing the configuration.
     * @return Either the value of the parameter as a <code>String</code> or <code>null</code>.
     */
    public String stringValue(final Vertx vertx) {
        final var ret = vertx.getOrCreateContext().config().getString(key);
        LOGGER.info("Using configuration value: {} for key: {}.", ret, key);
        return ret;
    }

    /**
     * Provides the integer value of this parameter from the Vert.x configuration or the <code>defaultValue</code> if
     * there was none.
     *
     * @param vertx The <code>Vertx</code> instance containing the configuration.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as an <code>int</code> or the <code>defaultValue</code>.
     * @throws ClassCastException If the value was not an integer.
     */
    public int intValue(final Vertx vertx, final int defaultValue) {
        final var value = vertx.getOrCreateContext().config().getInteger(key);
        final var ret = value == null ? defaultValue : value;
        LOGGER.info("Using configuration value: {} for key: {}.", ret, key);
        return ret;
    }

    /**
     * Provides the JSON value of this parameter from the Vert.x configuration or the <code>defaultValue</code> if there
     * was none.
     *
     * @param vertx The <code>Vertx</code> instance containing the configuration.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as a JSON object or the <code>defaultValue</code>.
     */
    public JsonObject jsonValue(final Vertx vertx, final JsonObject defaultValue) {
        final var value = vertx.getOrCreateContext().config().getJsonObject(key);
        final var ret = value == null ? defaultValue : value;
        LOGGER.info("Read json value {} for key: {}.", ret, key);
        return ret;
    }

    /**
     * Provides the JSON value of this parameter from the Vert.x configuration or the <code>null</code> if there was
     * none.
     *
     * @param vertx The <code>Vertx</code> instance containing the configuration.
     * @return Either the value of the parameter as a JSON object or the <code>defaultValue</code>.
     */
    @SuppressWarnings("unused")
    public JsonObject jsonValue(final Vertx vertx) {
        final var value = vertx.getOrCreateContext().config().getJsonObject(key);
        LOGGER.info("Read json value {} for key: {}.", value, key);
        return value;
    }
}
