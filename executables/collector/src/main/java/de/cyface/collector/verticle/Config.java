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

import org.apache.commons.lang3.Validate;

import de.cyface.api.AuthenticatedEndpointConfig;
import de.cyface.api.EndpointConfig;
import de.cyface.api.InvalidConfiguration;
import de.cyface.api.Parameter;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.MongoClient;

/**
 * Configuration parameters required to start the HTTP server which also handles routing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public class Config implements AuthenticatedEndpointConfig {

    /**
     * The number of bytes in one megabyte. This can be used to limit the amount of data accepted by the server.
     */
    private static final long BYTES_IN_ONE_MEGABYTE = 1_048_576L;
    /**
     * The default number of seconds the JWT authentication token is valid after login.
     */
    private static final int DEFAULT_PAYLOAD_LIMIT = Math.toIntExact(BYTES_IN_ONE_MEGABYTE * 100L);
    /**
     * The default number of milliseconds to wait before removing cached uploads after last modification: 7 days.
     */
    private static final long DEFAULT_UPLOAD_EXPIRATION_TIME = 1000 * 60 * 60 * 24 * 7;
    /**
     * A parameter for the endpoint path the API service is available at.
     */
    private static final Parameter HTTP_ENDPOINT = Parameter.HTTP_ENDPOINT_V3;
    /**
     * The port on which the HTTP server should listen
     */
    private final int httpPort;
    /**
     * The hostname of the HTTP server.
     */
    private final String host;
    /**
     * The endpoint the HTTP server should listen on.
     */
    private final String endpoint;
    /**
     * The client to use to access the Mongo database holding the uploaded data
     */
    private final MongoClient dataDatabase;
    /**
     * The client to use to access the Mongo database holding the user account data
     */
    private final MongoClient userDatabase;
    /**
     * {@code null} or the Authenticator that uses the Mongo user database to store and retrieve credentials.
     */
    private final MongoAuthentication authProvider;
    /**
     * The public key used to check the validity of JWT tokens used for authentication
     */
    private final String publicKey;
    /**
     * The private key used to issue new valid JWT tokens
     */
    private final String privateKey;
    /**
     * The auth provider to be used for authentication.
     */
    private final JWTAuth jwtAuthProvider;
    /**
     * The issuer to be used for authentication.
     */
    private final String issuer;
    /**
     * The audience to be used for authentication.
     */
    private final String audience;
    /**
     * A parameter telling the system how long a new JWT token stays valid in seconds.
     */
    private final int tokenExpirationTime;
    /**
     * A parameter telling the system how large measurement uploads may be.
     */
    private final long measurementLimit;
    /**
     * A parameter telling the system the milliseconds to wait before removing cached uploads after last modification.
     */
    private final long uploadExpirationTime;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @throws IOException if key files are inaccessible
     */
    public Config(final Vertx vertx) throws IOException {
        checkValidConfiguration(vertx.getOrCreateContext().config());

        // Data-database client
        final var mongoDatabaseConfiguration = Parameter.MONGO_DATA_DB.jsonValue(vertx);
        this.dataDatabase = EndpointConfig.createSharedMongoClient(vertx, mongoDatabaseConfiguration);

        // User-database client
        final var mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(vertx, new JsonObject());
        this.userDatabase = AuthenticatedEndpointConfig.createSharedMongoClient(vertx, mongoUserDatabaseConfiguration);
        this.authProvider = AuthenticatedEndpointConfig.buildMongoAuthProvider(userDatabase);

        // Http config
        this.host = Parameter.HTTP_HOST.stringValue(vertx);
        this.httpPort = Parameter.HTTP_PORT.intValue(vertx, DEFAULT_HTTP_PORT);
        this.endpoint = HTTP_ENDPOINT.stringValue(vertx);

        // JWT config
        this.publicKey = AuthenticatedEndpointConfig.extractKey(vertx, Parameter.JWT_PUBLIC_KEY_FILE_PATH);
        this.privateKey = AuthenticatedEndpointConfig.extractKey(vertx, Parameter.JWT_PRIVATE_KEY_FILE_PATH);
        this.jwtAuthProvider = AuthenticatedEndpointConfig.createAuthProvider(vertx, publicKey, privateKey);
        this.issuer = AuthenticatedEndpointConfig.jwtIssuer(host, endpoint);
        this.audience = AuthenticatedEndpointConfig.jwtAudience(host, endpoint);
        this.tokenExpirationTime = Parameter.TOKEN_EXPIRATION_TIME.intValue(vertx, DEFAULT_TOKEN_VALIDATION_TIME);
        this.measurementLimit = Parameter.MEASUREMENT_PAYLOAD_LIMIT.longValue(vertx, DEFAULT_PAYLOAD_LIMIT);
        this.uploadExpirationTime = Parameter.UPLOAD_EXPIRATION_TIME.longValue(vertx, DEFAULT_UPLOAD_EXPIRATION_TIME);

        Validate.notNull(publicKey);
        Validate.notNull(privateKey);
        Validate.notEmpty(publicKey);
        Validate.notEmpty(privateKey);
        Validate.notEmpty(host, "Hostname not found. Please use the parameter %s.", Parameter.HTTP_HOST.key());
        Validate.isTrue(httpPort > 0);
        Validate.notEmpty(endpoint, "Endpoint not found. Please use the parameter %s.", HTTP_ENDPOINT.key());
        Validate.isTrue(tokenExpirationTime > 0);
        Validate.isTrue(uploadExpirationTime > 0);
    }

    /**
     * Checks if the provided configuration is valid for a Cyface data collector.
     *
     * @param config The configuration to check.
     */
    public void checkValidConfiguration(final JsonObject config) {
        if (config.getString(Parameter.HTTP_HOST.key()) == null || config.getString(HTTP_ENDPOINT.key()) == null) {
            throw new InvalidConfiguration(config);
        }
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getHost() {
        return host;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public MongoClient getDataDatabase() {
        return dataDatabase;
    }

    public MongoClient getUserDatabase() {
        return userDatabase;
    }

    public MongoAuthentication getAuthProvider() {
        return authProvider;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public JWTAuth getJwtAuthProvider() {
        return jwtAuthProvider;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getAudience() {
        return audience;
    }

    public int getTokenExpirationTime() {
        return tokenExpirationTime;
    }

    public long getMeasurementLimit() {
        return measurementLimit;
    }

    public long getUploadExpirationTime() {
        return uploadExpirationTime;
    }
}
