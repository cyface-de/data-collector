/*
 * Copyright 2018, 2019, 2020 Cyface GmbH
 * This file is part of the Cyface Data Collector.
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.verticle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.Hasher;
import de.cyface.collector.MongoDbUtils;
import de.cyface.collector.Parameter;
import de.cyface.collector.handler.AuthenticationFailureHandler;
import de.cyface.collector.handler.AuthenticationHandler;
import de.cyface.collector.handler.FailureHandler;
import de.cyface.collector.handler.MeasurementHandler;
import de.cyface.collector.handler.UserCreationHandler;
import de.cyface.collector.model.Measurement;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.7
 * @since 2.0.0
 */
public final class CollectorApiVerticle extends AbstractVerticle {

    /**
     * The <code>Logger</code> used for objects of this class. Configure it by changing the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectorApiVerticle.class);
    /**
     * The hashing algorithm used for public and private keys to generate and check JWT tokens.
     */
    public static final String JWT_HASH_ALGORITHM = "HS256";
    /**
     * The number of bytes in one gigabyte. This can be used to limit the amount of data accepted by the server.
     */
    private static final long BYTES_IN_ONE_GIGABYTE = 1_073_741_824L;
    /**
     * The number of bytes in one kilobyte. This is used to limit the amount of bytes accepted by an authentication
     * request.
     */
    private static final long BYTES_IN_ONE_KILOBYTE = 1024L;
    /**
     * The role which identifies users with "admin" privileges.
     */
    private static final String ADMIN_ROLE = "admin";

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        // Setup Measurement event bus
        prepareEventBus();

        // Setup mongo user database client with authProvider
        final var mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(vertx, new JsonObject());
        final var client = MongoDbUtils.createSharedMongoClient(vertx, mongoUserDatabaseConfiguration);
        final var salt = loadSalt(vertx);
        final var authProvider = MongoDbUtils.buildMongoAuthProvider(client);

        // Start http server with auth config
        final var httpPort = Parameter.COLLECTOR_HTTP_PORT.intValue(vertx, 8080);
        final var publicKey = extractKey(Parameter.JWT_PUBLIC_KEY_FILE_PATH);
        Validate.notNull(publicKey,
                "Unable to load public key for JWT authentication. Did you provide a valid PEM file using the parameter "
                        + Parameter.JWT_PUBLIC_KEY_FILE_PATH.key() + ".");
        final var privateKey = extractKey(Parameter.JWT_PRIVATE_KEY_FILE_PATH);
        Validate.notNull(privateKey,
                "Unable to load private key for JWT authentication. Did you provide a valid PEM file using the parameter "
                        + Parameter.JWT_PRIVATE_KEY_FILE_PATH.key() + ".");
        final Promise<Void> serverStartFuture = Promise.promise();
        setupRoutes(publicKey, privateKey, authProvider, result -> {
            if (result.succeeded()) {
                final var router = result.result();
                startHttpServer(router, serverStartFuture, httpPort);
            } else {
                serverStartFuture.fail(result.cause());
            }
        });

        // Insert default admin user
        final var adminUsername = Parameter.ADMIN_USER_NAME.stringValue(vertx, "admin");
        final var adminPassword = Parameter.ADMIN_PASSWORD.stringValue(vertx, "secret");
        final var defaultUserCreatedFuture = Promise.promise();
        client.findOne("user", new JsonObject().put("username", adminUsername), null, result -> {
            if (result.failed()) {
                defaultUserCreatedFuture.fail(result.cause());
                return;
            }
            if (result.result() == null) {
                final var hasher = new Hasher(HashingStrategy.load(), salt.getBytes(StandardCharsets.UTF_8));
                final var userCreationHandler = new UserCreationHandler(client, "user", hasher);
                userCreationHandler.createUser(adminUsername, adminPassword, ADMIN_ROLE, success -> {
                    LOGGER.info("Identifier of new user id: {}", success);
                    defaultUserCreatedFuture.complete();
                }, failure -> {
                    LOGGER.error("Unable to create default user!");
                    defaultUserCreatedFuture.fail(failure);
                });
            } else {
                defaultUserCreatedFuture.complete();
            }
        });

        // Block until all futures completed
        final var startUpFinishedFuture = CompositeFuture.all(serverStartFuture.future(),
                defaultUserCreatedFuture.future());
        startUpFinishedFuture.onComplete(r -> {
            if (r.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(r.cause());
            }
        });

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
        final var salt = Parameter.SALT.stringValue(vertx);
        final var saltPath = Parameter.SALT_PATH.stringValue(vertx);
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
     * Prepares the Vertx event bus during startup of the application.
     */
    private void prepareEventBus() {
        vertx.eventBus().registerDefaultCodec(Measurement.class, Measurement.getCodec());
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param publicKey The public key used to check the validity of JWT tokens used for authentication.
     * @param privateKey The private key used to issue new valid JWT tokens.
     * @param mongoAuthProvider Authentication provider used to check for valid user accounts used to generate new JWT
     *            token.
     * @param next The handler to call when the router has been created.
     */
    private void setupRoutes(final String publicKey, final String privateKey,
            final MongoAuthentication mongoAuthProvider,
            final Handler<AsyncResult<Router>> next) {
        Validate.notEmpty(publicKey);
        Validate.notEmpty(privateKey);
        Validate.notNull(mongoAuthProvider);
        Validate.notNull(next);

        // Routing
        // OpenAPI3RouterFactory.create(vertx, this.getClass().getResource("/webroot/openapi.yml").getFile(), r -> {
        // if (r.succeeded()) {
        // OpenAPI3RouterFactory routerFactory = r.result();
        // routerFactory.setBodyHandler(BodyHandler.create().setDeleteUploadedFilesOnEnd(false));
        // routerFactory.addHandlerByOperationId("uploadMeasurement", new MeasurementHandler());
        // routerFactory.addHandlerByOperationId("login",
        // new AuthenticationHandler(mongoAuthProvider, jwtAuthProvider));
        // routerFactory.addSecurityHandler("bearerAuth", JWTAuthHandler.create(jwtAuthProvider));
        // routerFactory.addFailureHandlerByOperationId("uploadMeasurement", new FailureHandler());
        // routerFactory.addFailureHandlerByOperationId("login", new FailureHandler());
        // routerFactory.setNotImplementedFailureHandler(result -> result.response().setStatusCode(501).end());
        // routerFactory.setValidationFailureHandler(result -> result.response().setStatusCode(400).end());
        // final Router router = routerFactory.getRouter();
        // next.handle(Future.succeededFuture(router));
        // } else {
        // LOGGER.error("Failed loading API specification!");
        // next.handle(Future.failedFuture(r.cause()));
        // }
        // });
        final var mainRouter = Router.router(vertx);
        final var v2ApiRouter = Router.router(vertx);
        final var host = Parameter.COLLECTOR_HOST.stringValue(vertx);
        Validate.notEmpty(host, "Hostname not found. Please provide it using the %s parameter!",
                Parameter.COLLECTOR_HOST.key());
        final var endpoint = Parameter.COLLECTOR_ENDPOINT.stringValue(vertx);
        Validate.notEmpty(endpoint, "Endpoint not found. Please provide it using the %s parameter!",
                Parameter.COLLECTOR_ENDPOINT.key());

        // Set up authentication check
        final var keyOptions = new PubSecKeyOptions().setAlgorithm(JWT_HASH_ALGORITHM).setBuffer(publicKey)
                .setBuffer(privateKey);
        final var issuer = jwtIssuer(host, endpoint);
        final var config = new JWTAuthOptions().addPubSecKey(keyOptions)
                .setJWTOptions(new JWTOptions().setIssuer(issuer)
                        .setAudience(Collections.singletonList(jwtAudience(host, endpoint))));
        final var jwtAuthProvider = JWTAuth.create(vertx, config);
        // jwtAuthProvider.

        // Set up default routes
        mainRouter.mountSubRouter(endpoint, v2ApiRouter);

        // Set up v2 API
        // Set up authentication route
        v2ApiRouter.route("/login").consumes("application/json")
                .handler(BodyHandler.create().setBodyLimit(2 * BYTES_IN_ONE_KILOBYTE))
                .handler(LoggerHandler.create())
                .handler(new AuthenticationHandler(mongoAuthProvider, jwtAuthProvider, issuer,
                        jwtAudience(host, endpoint)))
                .failureHandler(new AuthenticationFailureHandler());
        // Set up data collector route
        v2ApiRouter.post("/measurements").consumes("multipart/form-data")
                .handler(BodyHandler.create().setBodyLimit(BYTES_IN_ONE_GIGABYTE).setDeleteUploadedFilesOnEnd(true))
                .handler(LoggerHandler.create())
                .handler(JWTAuthHandler.create(jwtAuthProvider))
                .handler(new MeasurementHandler(MongoClient.create(vertx, config().getJsonObject("mongo.datadb"))))
                .failureHandler(ErrorHandler.create(vertx));
        // .failureHandler(new AuthenticationFailureHandler());
        // Set up web api
        v2ApiRouter.route().handler(StaticHandler.create("webroot/api"));
        // Set up failure handler for all other resources
        mainRouter.route("/*").last().handler(new FailureHandler());

        /*
         * This implementation does not require a future since everything is synchronous but it will require this if we
         * add the OpenApi generated route.
         */
        next.handle(Future.succeededFuture(mainRouter));
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
     * Starts the HTTP server provided by this application. This server runs the Cyface Collector REST-API.
     *
     * @param router The router for all the endpoints the HTTP server should serve.
     * @param startFuture Informs the caller about the successful or failed start of the server.
     * @param httpPort The HTTP port to run the server at.
     */
    private void startHttpServer(final Router router, final Promise<Void> startFuture, final int httpPort) {
        Validate.notNull(router);
        Validate.notNull(startFuture);
        Validate.isTrue(httpPort > 0);

        vertx.createHttpServer().requestHandler(router).listen(httpPort,
                serverStartup -> completeStartup(serverStartup, startFuture));
    }

    /**
     * Finishes the <code>CollectorApiVerticle</code> startup process and informs all interested parties about whether
     * it has
     * been successful or not.
     *
     * @param serverStartup The result of the server startup as provided by <code>Vertx</code>.
     * @param future A future to call to inform all waiting parties about success or failure of the startup process.
     */
    private void completeStartup(final AsyncResult<HttpServer> serverStartup, final Promise<Void> future) {
        if (serverStartup.succeeded()) {
            future.complete();
            LOGGER.info("Successfully started Collector API!");
        } else {
            future.fail(serverStartup.cause());
            LOGGER.info("Starting Collector API failed!");
        }
    }

    /**
     * Extracts a key from a PEM keyfile.
     *
     * @param keyParameter The Vertx configuration parameter specifying the location of the file containing the key.
     * @return The extracted key.
     * @throws IOException If the key file was not accessible.
     */
    private String extractKey(final Parameter keyParameter) throws IOException {
        final var keyFilePath = keyParameter.stringValue(vertx, null);
        if (keyFilePath == null) {
            return null;
        }
        return Files.readString(Path.of(keyFilePath));
    }
}
