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
package de.cyface.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.lang3.Validate;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import io.vertx.ext.mongo.MongoClient;

import static de.cyface.api.Authenticator.JWT_HASH_ALGORITHM;

/**
 * Configuration parameters required to start the HTTP server which also handles routing.
 * <p>
 * TODO: Should be refactored [CY-5602]
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 5.3.0
 */
public class ServerConfig {

    /**
     * The port on which the HTTP server should listen if no port was specified.
     */
    private static final int DEFAULT_HTTP_PORT = 8080;
    /**
     * The default number of seconds the JWT authentication token is valid after login.
     */
    private static final int DEFAULT_TOKEN_VALIDATION_TIME = 120;
    /**
     * If no salt value was provided this default value is used.
     */
    public static final String DEFAULT_CYFACE_SALT = "cyface-salt";
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
     * The port on which the HTTP server should listen
     */
    private final int httpPort;
    /**
     * The public key used to check the validity of JWT tokens used for authentication
     */
    private final String publicKey;
    /**
     * The private key used to issue new valid JWT tokens
     */
    private final String privateKey;
    /**
     * The hostname of the HTTP server.
     */
    private final String host;
    /**
     * The endpoint the HTTP server should listen on.
     */
    private final String endpoint;
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
     * The default data source name to use for user and data database if none is provided via configuration.
     */
    private static final String DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface";

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @throws IOException if key files are inaccessible
     */
    public ServerConfig(final Vertx vertx) throws IOException {
        checkValidConfiguration(vertx.getOrCreateContext().config());

        // Data-database client
        final var mongoDatabaseConfiguration = Parameter.MONGO_DATA_DB.jsonValue(vertx);
        this.dataDatabase = createSharedMongoClient(vertx, mongoDatabaseConfiguration);

        // User-database client
        final var mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(vertx, new JsonObject());
        this.userDatabase = createSharedMongoClient(vertx, mongoUserDatabaseConfiguration);

        // Http config
        this.host = Parameter.HTTP_HOST.stringValue(vertx);
        this.endpoint = Parameter.HTTP_ENDPOINT.stringValue(vertx);
        this.httpPort = Parameter.HTTP_PORT.intValue(vertx, DEFAULT_HTTP_PORT);

        // Auth provider config
        this.publicKey = extractKey(vertx, Parameter.JWT_PUBLIC_KEY_FILE_PATH);
        this.privateKey = extractKey(vertx, Parameter.JWT_PRIVATE_KEY_FILE_PATH);
        this.jwtAuthProvider = createAuthProvider(vertx, publicKey, privateKey);
        this.issuer = jwtIssuer(host, endpoint);
        this.audience = jwtAudience(host, endpoint);
        this.tokenExpirationTime = Parameter.TOKEN_EXPIRATION_TIME.intValue(vertx, DEFAULT_TOKEN_VALIDATION_TIME);
        this.authProvider = buildMongoAuthProvider(userDatabase);

        Validate.notNull(publicKey);
        Validate.notNull(privateKey);
        Validate.notEmpty(publicKey);
        Validate.notEmpty(privateKey);
        Validate.notEmpty(host, "Hostname not found. Please provide it using the %s parameter!",
                Parameter.HTTP_HOST.key());
        Validate.notEmpty(endpoint, "Endpoint not found. Please provide it using the %s parameter!",
                Parameter.HTTP_ENDPOINT.key());
        Validate.isTrue(httpPort > 0);
    }

    /**
     * Checks if the provided configuration is valid for a Cyface data collector.
     *
     * @param config The configuration to check.
     */
    private void checkValidConfiguration(final JsonObject config) {
        if (config.getString(Parameter.HTTP_HOST.key()) == null
                || config.getString(Parameter.HTTP_ENDPOINT.key()) == null) {
            throw new InvalidConfiguration(config);
        }
    }

    /**
     * Creates a JWT auth provider.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @param publicKey The public key to be used for authentication
     * @param privateKey The private key to be used for authentication
     * @return The auth provider
     */
    public JWTAuth createAuthProvider(final Vertx vertx, final String publicKey, final String privateKey) {
        return JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm(JWT_HASH_ALGORITHM)
                        .setBuffer(publicKey))
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm(JWT_HASH_ALGORITHM)
                        .setBuffer(privateKey)));
    }

    /**
     * Provides the value for the JWT audience information.
     *
     * @param host The host this service runs under
     * @param endpoint The endpoint path this service runs under
     * @return The JWT audience as a <code>String</code>
     */
    private String jwtAudience(final String host, final String endpoint) {
        return String.format("%s%s", host, endpoint);
    }

    /**
     * Provides the value for the JWT issuer information.
     *
     * @param host The host this service runs under
     * @param endpoint The endpoint path this service runs under
     * @return The JWT issuer as a <code>String</code>
     */
    private String jwtIssuer(final String host, final String endpoint) {
        return String.format("%s%s", host, endpoint);
    }

    /**
     * Extracts a key from a PEM key-file.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @param keyParameter The Vertx configuration parameter specifying the location of the file containing the key
     * @return The extracted key, including the lines starting with `-----`, as required when using {@code RS256}.
     * @throws FileNotFoundException If the key file was not found
     * @throws IOException If the key file was not accessible
     */
    public String extractKey(final Vertx vertx, final Parameter keyParameter)
            throws FileNotFoundException, IOException {
        final var keyFilePath = keyParameter.stringValue(vertx, null);
        if (keyFilePath == null) {
            return null;
        }

        return Files.readString(Path.of(keyFilePath));
    }

    /**
     * Loads the external encryption salt from the Vertx configuration. If no value was provided the default value
     * {@code #DEFAULT_CYFACE_SALT} is used.
     * <p>
     * Asynchronous implementation as in Vert.X you can only access files asynchronously.
     *
     * @param vertx The current <code>Vertx</code> instance
     * @return The <code>Promise</code> about a value to be used as encryption salt
     */
    public static Promise<String> loadSalt(final Vertx vertx) {
        final Promise<String> result = Promise.promise();
        final var salt = Parameter.SALT.stringValue(vertx);
        final var saltPath = Parameter.SALT_PATH.stringValue(vertx);
        if (salt == null && saltPath == null) {
            result.complete(DEFAULT_CYFACE_SALT);
        } else if (salt != null && saltPath != null) {
            result.fail("Please provide either a salt value or a path to a salt file. "
                    + "Encountered both and can not decide which to use. Aborting!");
        } else if (salt != null) {
            result.complete(salt);
        } else {
            final var fileSystem = vertx.fileSystem();
            fileSystem.readFile(saltPath, readFileResult -> {
                if (readFileResult.failed()) {
                    result.fail(readFileResult.cause());
                } else {
                    final var loadedSalt = readFileResult.result().toString(StandardCharsets.UTF_8);
                    result.complete(loadedSalt);
                }
            });
        }
        return result;
    }

    /**
     * @param client A Mongo client to access the user Mongo database.
     * @return The <code>Promise</code> about an Authentication provider used to check for valid user accounts used to
     *         generate new JWT token.
     */
    public static MongoAuthentication buildMongoAuthProvider(final MongoClient client) {

        // From Collector.MongoDbUtils
        final var authProperties = new MongoAuthenticationOptions();
        return MongoAuthentication.create(client, authProperties);
    }

    /**
     * Creates a shared Mongo database client for the provided configuration.
     *
     * @param vertx The <code>Vertx</code> instance to create the client from.
     * @param config Configuration of the newly created client. For further information refer to
     *            {@link Parameter#MONGO_DATA_DB} and {@link Parameter#MONGO_USER_DB}.
     * @return A <code>MongoClient</code> ready for usage.
     */
    public static MongoClient createSharedMongoClient(final Vertx vertx, final JsonObject config) {
        Objects.requireNonNull(config, String.format(
                "Unable to load Mongo user database configuration. "
                        + "Please provide a valid configuration using the %s parameter and at least as \"db_name\", "
                        + "a \"connection_string\" and a \"data_source_name\"! Also check if your database is running "
                        + "and accessible!",
                Parameter.MONGO_USER_DB.key()));
        final var dataSourceName = config.getString("data_source_name", DEFAULT_MONGO_DATA_SOURCE_NAME);
        return MongoClient.createShared(vertx, config, dataSourceName);
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "dataDatabase=" + dataDatabase +
                ", userDatabase=" + userDatabase +
                ", authProvider=" + authProvider +
                ", httpPort=" + httpPort +
                ", publicKey='" + publicKey + '\'' +
                ", privateKey='" + privateKey + '\'' +
                ", host='" + host + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", jwtAuthProvider=" + jwtAuthProvider +
                ", issuer='" + issuer + '\'' +
                ", audience='" + audience + '\'' +
                ", tokenExpirationTime=" + tokenExpirationTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ServerConfig that = (ServerConfig)o;
        return httpPort == that.httpPort &&
                Objects.equals(dataDatabase, that.dataDatabase) &&
                Objects.equals(userDatabase, that.userDatabase) &&
                Objects.equals(authProvider, that.authProvider) &&
                Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(privateKey, that.privateKey) &&
                Objects.equals(host, that.host) &&
                Objects.equals(endpoint, that.endpoint) &&
                Objects.equals(jwtAuthProvider, that.jwtAuthProvider) &&
                Objects.equals(issuer, that.issuer) &&
                Objects.equals(audience, that.audience) &&
                Objects.equals(tokenExpirationTime, that.tokenExpirationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataDatabase, userDatabase, authProvider, httpPort, publicKey, privateKey, host, endpoint,
                jwtAuthProvider, issuer, audience, tokenExpirationTime);
    }

    public int getTokenExpirationTime() {
        return tokenExpirationTime;
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

    public int getHttpPort() {
        return httpPort;
    }

    @SuppressWarnings("unused") // Part of this class' API
    public String getPublicKey() {
        return publicKey;
    }

    @SuppressWarnings("unused") // Part of this class' API
    public String getPrivateKey() {
        return privateKey;
    }

    public String getHost() {
        return host;
    }

    public String getEndpoint() {
        return endpoint;
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
}
