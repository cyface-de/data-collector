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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * Configuration parameters required to start the HTTP server which also handles routing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public interface AuthenticatedEndpointConfig extends EndpointConfig {

    /**
     * The default number of seconds the JWT authentication token is valid after login.
     * <p>
     * Using 10 minutes as preparing a 15-hour measurement for upload took 2.5 minutes on a sample phone [CY-5699].
     */
    int DEFAULT_TOKEN_VALIDATION_TIME = 600;
    /**
     * If no salt value was provided this default value is used.
     */
    String DEFAULT_CYFACE_SALT = "cyface-salt";
    /**
     * The default data source name to use for user and data database if none is provided via configuration.
     */
    String DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface";

    /**
     * Creates a JWT auth provider.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @param publicKey The public key to be used for authentication
     * @param privateKey The private key to be used for authentication
     * @return The auth provider
     */
    static JWTAuth createAuthProvider(final Vertx vertx, final String publicKey, final String privateKey) {
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
    static String jwtAudience(final String host, final String endpoint) {
        return String.format("%s%s", host, endpoint);
    }

    /**
     * Provides the value for the JWT issuer information.
     *
     * @param host The host this service runs under
     * @param endpoint The endpoint path this service runs under
     * @return The JWT issuer as a <code>String</code>
     */
    static String jwtIssuer(final String host, final String endpoint) {
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
    static String extractKey(final Vertx vertx, final Parameter keyParameter)
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
     * The salt is only needed to generate a hash, not to check a password against a hash as the salt is stored at the
     * beginning of the hash. This way the salt can be changed without invalidating all previous hashes.
     * <p>
     * Asynchronous implementation as in Vert.X you can only access files asynchronously.
     *
     * @param vertx The current <code>Vertx</code> instance
     * @return The <code>Promise</code> about a value to be used as encryption salt
     */
    static Promise<String> loadSalt(final Vertx vertx) {
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
    static MongoAuthentication buildMongoAuthProvider(final MongoClient client) {
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
    static MongoClient createSharedMongoClient(final Vertx vertx, final JsonObject config) {
        Objects.requireNonNull(config, String.format(
                "Unable to load Mongo user database configuration. "
                        + "Please provide a valid configuration using the %s parameter and at least as \"db_name\", "
                        + "a \"connection_string\" and a \"data_source_name\"! Also check if your database is running "
                        + "and accessible!",
                Parameter.MONGO_USER_DB.key()));
        final var dataSourceName = config.getString("data_source_name", DEFAULT_MONGO_DATA_SOURCE_NAME);
        return MongoClient.createShared(vertx, config, dataSourceName);
    }

    MongoClient getUserDatabase();

    MongoAuthentication getAuthProvider();

    String getPublicKey();

    String getPrivateKey();

    JWTAuth getJwtAuthProvider();

    String getIssuer();

    String getAudience();

    int getTokenExpirationTime();
}
