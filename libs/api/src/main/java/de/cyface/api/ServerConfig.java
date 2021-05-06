/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.api;

import static de.cyface.api.Authenticator.JWT_HASH_ALGORITHM;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.commons.lang3.Validate;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
 * @version 1.0.1
 * @since 1.0.0
 */
public class ServerConfig {

    /**
     * The port on which the HTTP server should listen if no port was specified.
     */
    private static final int DEFAULT_HTTP_PORT = 8081;
    /**
     * The default number of seconds the JWT authentication token is valid after login.
     */
    private static final int DEFAULT_TOKEN_VALIDATION_TIME = 3600;
    /**
     * The client to use to access the Mongo database holding the uploaded data
     */
    private final MongoClient dataDatabase;
    /**
     * The client to use to access the Mongo database holding the user account data
     */
    private final MongoClient userDatabase;
    /**
     * Authentication provider used to check for valid user accounts used to generate new JWT token
     */
    private final MongoAuth authProvider;
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
     * The number of seconds the JWT authentication token is valid after login.
     */
    private final int tokenValidationTime;
    /**
     * The default data source name to use for user and data database if none is provided via configuration.
     */
    private static final String DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface";

    /**
     * @param vertx The Vertx instance to get the parameters from
     * @throws IOException if key files are inaccessible
     */
    public ServerConfig(final Vertx vertx) throws IOException {
        checkValidConfiguration(vertx.getOrCreateContext().config());

        // Data-database client
        final JsonObject mongoDatabaseConfiguration = Parameter.MONGO_DATA_DB.jsonValue(vertx, new JsonObject());
        this.dataDatabase = createSharedMongoClient(vertx, mongoDatabaseConfiguration);

        // User-database client
        final JsonObject mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(vertx, new JsonObject());
        this.userDatabase = createSharedMongoClient(vertx, mongoUserDatabaseConfiguration);

        // Http config
        this.host = Parameter.HTTP_HOST.stringValue(vertx);
        this.endpoint = Parameter.HTTP_ENDPOINT.stringValue(vertx);

        // Auth provider config
        final String salt = loadSalt(vertx);
        this.authProvider = buildMongoAuthProvider(userDatabase, salt);
        this.httpPort = Parameter.HTTP_PORT.intValue(vertx, DEFAULT_HTTP_PORT);
        this.publicKey = extractKey(vertx, Parameter.JWT_PUBLIC_KEY_FILE_PATH);
        this.privateKey = extractKey(vertx, Parameter.JWT_PRIVATE_KEY_FILE_PATH);
        this.jwtAuthProvider = createAuthProvider(vertx, publicKey, privateKey);
        this.issuer = String.format("%s%s", host, endpoint);
        this.audience = issuer;
        this.tokenValidationTime = Parameter.TOKEN_VALIDATION_TIME.intValue(vertx, DEFAULT_TOKEN_VALIDATION_TIME);

        Validate.notNull(publicKey);
        Validate.notNull(privateKey);
        Validate.notEmpty(publicKey);
        Validate.notEmpty(privateKey);
        Validate.notNull(authProvider);
        Validate.notEmpty(host, "Hostname not found. Please provide it using the %s parameter!",
                Parameter.HTTP_HOST.key());
        Validate.notEmpty(endpoint, "Endpoint not found. Please provide it using the %s parameter!",
                Parameter.HTTP_ENDPOINT.key());
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
        final PubSecKeyOptions keyOptions = new PubSecKeyOptions().setAlgorithm(JWT_HASH_ALGORITHM)
                .setPublicKey(publicKey)
                .setSecretKey(privateKey);
        final JWTAuthOptions config = new JWTAuthOptions().addPubSecKey(keyOptions);
        return JWTAuth.create(vertx, config);
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
     * @param vertx The current <code>Vertx</code> instance
     * @return A value to be used as encryption salt
     * @throws IOException If the salt is provided in a file and that file is not accessible
     */
    private String loadSalt(final Vertx vertx) throws IOException {
        final String salt = Parameter.SALT.stringValue(vertx);
        final String saltPath = Parameter.SALT_PATH.stringValue(vertx);
        if (salt == null && saltPath == null) {
            return "cyface-salt";
        } else if (salt != null && saltPath != null) {
            throw new InvalidConfigurationException(
                    "Please provide either a salt value or a path to a salt file. "
                            + "Encountered both and can not decide which to use. Aborting!");
        } else if (salt != null) {
            return salt;
        } else {
            return Files.readAllLines(Paths.get(saltPath)).get(0);
        }
    }

    /**
     * @param client A Mongo client to access the user Mongo database.
     * @param salt The salt used to make hacking passwords more complex.
     * @return Authentication provider used to check for valid user accounts used to generate new JWT token.
     */
    public static MongoAuth buildMongoAuthProvider(final MongoClient client, final String salt) {
        final JsonObject authProperties = new JsonObject();
        final MongoAuth authProvider = MongoAuth.create(client, authProperties);
        HashStrategy hashStrategy = authProvider.getHashStrategy();
        hashStrategy.setSaltStyle(HashSaltStyle.EXTERNAL);
        hashStrategy.setExternalSalt(salt);
        authProvider.setHashAlgorithm(HashAlgorithm.PBKDF2);

        return authProvider;
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
        final String dataSourceName = config.getString("data_source_name", DEFAULT_MONGO_DATA_SOURCE_NAME);
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
                ", tokenValidationTime=" + tokenValidationTime +
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
                Objects.equals(tokenValidationTime, that.tokenValidationTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataDatabase, userDatabase, authProvider, httpPort, publicKey, privateKey, host, endpoint,
                jwtAuthProvider, issuer, audience, tokenValidationTime);
    }

    public int getTokenValidationTime() {
        return tokenValidationTime;
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
}
