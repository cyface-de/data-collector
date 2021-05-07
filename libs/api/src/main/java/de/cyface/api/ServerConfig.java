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

import static de.cyface.api.Authenticator.JWT_HASH_ALGORITHM;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;

import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import org.apache.commons.lang3.Validate;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.HashStrategy;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;

/**
 * Configuration parameters required to start the HTTP server which also handles routing.
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
    private static final int DEFAULT_TOKEN_VALIDATION_TIME = 60;
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
    private MongoAuthentication authProvider;
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
     * {@code null} or the salt used to encrypt user passwords on this server
     */
    private String salt;
    /**
     * A parameter telling the system how long a new JWT token stays valid in seconds.
     */
    private final int tokenExpirationTime;
    /**
     * The default data source name to use for user and data database if none is provided via configuration.
     */
    private static final String DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface";

    /**
     * Creates a *not fully* initialized instance of this class.
     * FIXME: You need to call `loadSalt()` and if the promise is successful call `lateInit(salt)`.
     *
     * K-FIX> Just have multiple static method in here which can be used on demand by the different
     * verticles instead of creating an instance which has all parameters which might not be needed
     * by all verticles in the future.
     *
     * A> So far I only found one Verticle which does not need all parts of this class.
     * This verticle just uses the static method createSharedMongoClient().
     * I.e. all others can simply abstract the configuration states in here. Makes it more readable.
     * the ManagementVerticle does not use the `ServerConfig` instances at all, just static access.
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
        // FIXME: currently needs to be called from outside: `loadSalt(vertx);` and after resolving `lateInit(salt)`
        this.publicKey = extractKey(vertx, Parameter.JWT_PUBLIC_KEY_FILE_PATH);
        this.privateKey = extractKey(vertx, Parameter.JWT_PRIVATE_KEY_FILE_PATH);
        this.jwtAuthProvider = createAuthProvider(vertx, publicKey, privateKey);
        this.issuer = String.format("%s%s", host, endpoint);
        this.audience = issuer;
        this.tokenExpirationTime = Parameter.TOKEN_EXPIRATION_TIME.intValue(vertx, DEFAULT_TOKEN_VALIDATION_TIME);

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

    public void lateInit(final String salt) {
        this.salt = salt;
        this.authProvider = buildMongoAuthProvider(userDatabase, salt);
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
        final var keyOptions = new PubSecKeyOptions().setAlgorithm(JWT_HASH_ALGORITHM)
                .setBuffer(publicKey)
                .setBuffer(privateKey);
        final var issuer = jwtIssuer(host, endpoint);
        final var config = new JWTAuthOptions().addPubSecKey(keyOptions)
                .setJWTOptions(new JWTOptions().setIssuer(issuer)
                        .setAudience(Collections.singletonList(jwtAudience(host, endpoint))));
        return JWTAuth.create(vertx, config);
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
     * @return The extracted key
     * @throws FileNotFoundException If the key file was not found
     * @throws IOException If the key file was not accessible
     */
    public String extractKey(final Vertx vertx, final Parameter keyParameter)
            throws FileNotFoundException, IOException {
        final String keyFilePath = keyParameter.stringValue(vertx, null);
        if (keyFilePath == null) {
            return null;
        }
        // FIXME: why did the Collector just use this simple code instead here?
        // return Files.readString(Path.of(keyFilePath));

        final StringBuilder keyBuilder = new StringBuilder();
        try (BufferedReader keyFileInput = new BufferedReader(
                new InputStreamReader(new FileInputStream(keyFilePath), StandardCharsets.UTF_8))) {

            String line = keyFileInput.readLine();
            while (line != null) {
                line = keyFileInput.readLine();
                if (line == null || line.startsWith("-----") || line.isEmpty()) {
                    // noinspection UnnecessaryContinue
                    continue;
                } else {
                    keyBuilder.append(line);
                }
            }

        }

        final String key = keyBuilder.toString();
        Validate.notNull(key, String.format(
                "Unable to load key for JWT authentication. Did you provide a valid PEM file using the parameter %s.",
                keyParameter.key()));
        return key;
    }

    /**
     * Loads the external encryption salt from the Vertx configuration. If no value was provided the default value
     * "cyface-salt" is used.
     *
     * Promise as you can only access files asynchronously
     *
     * @param vertx The current <code>Vertx</code> instance
     * @return The <code>Promise</code> about a value to be used as encryption salt
     */
    public Promise<String> loadSalt(final Vertx vertx) {
        final Promise<String> result = Promise.promise();
        final var salt = Parameter.SALT.stringValue(vertx);
        final var saltPath = Parameter.SALT_PATH.stringValue(vertx);
        if (salt == null && saltPath == null) {
            result.complete("cyface-salt");
        } else if (salt != null && saltPath != null) {
            result.fail("Please provide either a salt value or a path to a salt file. "
                    + "Encountered both and can not decide which to use. Aborting!");
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
     * @param salt The salt used to make hacking passwords more complex.
     * @return The <code>Promise</code> about an Authentication provider used to check for valid user accounts used to
     *         generate new JWT token.
     */
    public static MongoAuthentication buildMongoAuthProvider(final MongoClient client, final String salt) {
        // Old Backend libs api code
        // final JsonObject authProperties = new JsonObject();
        // final MongoAuth authProvider = MongoAuth.create(client, authProperties);
        // HashStrategy hashStrategy = authProvider.getHashStrategy();
        // hashStrategy.setSaltStyle(HashSaltStyle.EXTERNAL);
        // hashStrategy.setExternalSalt(salt);
        // authProvider.setHashAlgorithm(HashAlgorithm.PBKDF2);

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
                ", salt='" + salt + '\'' +
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
                Objects.equals(tokenExpirationTime, that.tokenExpirationTime) &&
                Objects.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataDatabase, userDatabase, authProvider, httpPort, publicKey, privateKey, host, endpoint,
                jwtAuthProvider, issuer, audience, tokenExpirationTime, salt);
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

    public MongoAuth getAuthProvider() {
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

    public String getSalt() {
        return salt;
    }
}
