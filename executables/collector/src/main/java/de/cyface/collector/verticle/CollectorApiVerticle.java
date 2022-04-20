/*
 * Copyright 2018-2022 Cyface GmbH
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

import static de.cyface.collector.handler.MeasurementHandler.FILE_UPLOADS_FOLDER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.api.Authenticator;
import de.cyface.api.FailureHandler;
import de.cyface.api.Hasher;
import de.cyface.api.MongoAuthHandler;
import de.cyface.api.Parameter;
import de.cyface.collector.handler.PreRequestHandler;
import de.cyface.collector.handler.UserCreationHandler;
import de.cyface.collector.handler.v2.MeasurementHandler;
import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 */
public final class CollectorApiVerticle extends AbstractVerticle {

    /**
     * The <code>Logger</code> used for objects of this class. Configure it by changing the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectorApiVerticle.class);
    /**
     * The role which identifies users with "admin" privileges.
     */
    private static final String ADMIN_ROLE = "admin";
    /**
     * The endpoint which accepts measurement uploads.
     */
    private static final String MEASUREMENTS_ENDPOINT = "/measurements";
    /**
     * The number of bytes in one gigabyte. This can be used to limit the amount of data accepted by the server.
     */
    private static final long BYTES_IN_ONE_GIGABYTE = 1_073_741_824L;
    /**
     * The number of bytes in one kilobyte. This can be used to limit the amount of data accepted by the server.
     */
    private static final long BYTES_IN_ONE_KILOBYTE = 1_024L;
    /**
     * The value to be used as encryption salt
     */
    private final String salt;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param salt The value to be used as encryption salt
     * @throws IOException if key files are inaccessible
     */
    public CollectorApiVerticle(final String salt) throws IOException {
        super();
        this.salt = salt;
    }

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        // Setup Measurement event bus
        prepareEventBus();

        // Load configurations
        final var configV2 = new de.cyface.collector.verticle.v2.Config(vertx);
        final var configV3 = new Config(vertx);

        // Create indices
        final var measurementIndex = new JsonObject().put("metadata.deviceId", 1).put("metadata.measurementId", 1);
        final var indexCreation = configV3.getDataDatabase().createIndex("fs.files", measurementIndex);

        // Start http server
        final var router = setupRoutes(configV2, configV3);
        final Promise<Void> serverStartPromise = Promise.promise();
        final var httpServer = new de.cyface.api.HttpServer(configV3.getHttpPort());
        httpServer.start(vertx, router, serverStartPromise);

        // Insert default admin user
        final var adminUsername = Parameter.ADMIN_USER_NAME.stringValue(vertx, "admin");
        final var adminPassword = Parameter.ADMIN_PASSWORD.stringValue(vertx, "secret");
        final var userCreation = createDefaultUser(configV3.getUserDatabase(), adminUsername, adminPassword);

        // Schedule upload file cleaner task
        vertx.setPeriodic(configV3.getUploadExpirationTime(), timerId -> {
            // Remove deprecated temp files
            // There is no way to look through all sessions to identify unreferenced files. Thus, we remove files which
            // have not been changed for a long time. The MeasurementHandler handles sessions with "missing" files.
            final var uploadFolder = FILE_UPLOADS_FOLDER.toFile();
            final var uploadFiles = uploadFolder.listFiles();
            if (uploadFiles != null) {
                Arrays.stream(uploadFiles).parallel().forEach(file -> {
                    Validate.isTrue(file.isFile());
                    final var lastModifiedMillis = file.lastModified();
                    if (lastModifiedMillis > configV3.getUploadExpirationTime()) {
                        LOGGER.debug(String.format("Cleaning up temp file: %s", file.getPath()));
                        final var deleted = file.delete();
                        if (!deleted) {
                            LOGGER.warn(String.format("Failed to remove temp file: %s", file.getPath()));
                        }
                    }
                });
            }
        });

        // Block until all futures completed
        final var startUpFinishedFuture = CompositeFuture.all(indexCreation, serverStartPromise.future(), userCreation);
        startUpFinishedFuture.onComplete(r -> {
            if (r.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(r.cause());
            }
        });
    }

    private Future<Void> createDefaultUser(final MongoClient userClient, final String adminUsername,
            final String adminPassword) {

        final Promise<Void> promise = Promise.promise();
        userClient.findOne("user", new JsonObject().put("username", adminUsername), null, result -> {
            if (result.failed()) {
                promise.fail(result.cause());
                return;
            }
            if (result.result() == null) {
                final var hasher = new Hasher(HashingStrategy.load(),
                        salt.getBytes(StandardCharsets.UTF_8));
                final var userCreationHandler = new UserCreationHandler(userClient, "user", hasher);
                final var userCreation = userCreationHandler.createUser(adminUsername, adminPassword, ADMIN_ROLE);
                userCreation.onSuccess(id -> {
                    LOGGER.info("Identifier of new user id: {}", id);
                    promise.complete();
                });
                userCreation.onFailure(e -> {
                    LOGGER.error("Unable to create default user!");
                    promise.fail(e);
                });
            } else {
                promise.complete();
            }
        });
        return promise.future();
    }

    /**
     * Prepares the Vertx event bus during startup of the application.
     */
    private void prepareEventBus() {
        vertx.eventBus().registerDefaultCodec(Measurement.class, Measurement.getCodec());
        vertx.eventBus().registerDefaultCodec(de.cyface.collector.model.v2.Measurement.class,
                de.cyface.collector.model.v2.Measurement.getCodec());
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param configV2 HTTP server configuration parameters required to set up the routes for API V2
     * @param configV3 HTTP server configuration parameters required to set up the routes for API V3
     * @return the created main {@code Router}
     */
    private Router setupRoutes(final de.cyface.collector.verticle.v2.Config configV2, final Config configV3) {

        // Setup V2 and V3 router
        final var mainRouter = Router.router(vertx);
        final var v2ApiRouter = Router.router(vertx);
        final var v3ApiRouter = Router.router(vertx);

        mainRouter.mountSubRouter(configV2.getEndpoint(), v2ApiRouter);
        mainRouter.mountSubRouter(configV3.getEndpoint(), v3ApiRouter);
        setupApiV2Router(v2ApiRouter, configV2);
        setupApiV3Router(v3ApiRouter, configV3);

        // Setup unknown-resource handler
        mainRouter.route("/*").last().handler(new FailureHandler());

        return mainRouter;
    }

    /**
     * Sets up the routing for the API Version 2.
     *
     * @param apiV2Router The sub-router to be used.
     * @param config HTTP server configuration parameters required to set up the routes
     */
    private void setupApiV2Router(Router apiV2Router, de.cyface.collector.verticle.v2.Config config) {

        // Setup measurement routes
        final var mongoAuthHandler = new MongoAuthHandler(config.getAuthProvider(), false);
        final var measurementHandler = new MeasurementHandler(config.getDataDatabase());
        final var failureHandler = ErrorHandler.create(vertx);
        final var bodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_GIGABYTE)
                .setDeleteUploadedFilesOnEnd(true);

        // Setup authentication
        Authenticator.setupAuthentication("/login", apiV2Router, config);

        // Register handlers
        addAuthenticatedPostV2Handler(apiV2Router, config.getJwtAuthProvider(), MEASUREMENTS_ENDPOINT,
                mongoAuthHandler, measurementHandler, failureHandler, bodyHandler);

        // Setup web-api route
        apiV2Router.route().handler(StaticHandler.create("webroot/api/v2"));
    }

    /**
     * Sets up the routing for the API Version 3.
     *
     * @param apiV3Router The sub-router to be used.
     * @param config HTTP server configuration parameters required to set up the routes
     */
    private void setupApiV3Router(Router apiV3Router, Config config) {

        // Setup measurement routes
        final var preRequestMongoAuthHandler = new MongoAuthHandler(config.getAuthProvider(), false);
        final var measurementMongoAuthHandler = new MongoAuthHandler(config.getAuthProvider(), true);
        final var preRequestHandler = new PreRequestHandler(config.getDataDatabase(), config.getMeasurementLimit());
        final var measurementHandler = new de.cyface.collector.handler.MeasurementHandler(
                config.getDataDatabase(), config.getMeasurementLimit());
        final var failureHandler = ErrorHandler.create(vertx);
        final var preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE);

        // Setup authentication
        Authenticator.setupAuthentication("/login", apiV3Router, config);

        // Setup session-handler
        // - for all handlers but login and, thus, registering this *after* login
        // - using `cookieless` as our clients pass the session-ID via URI
        final var store = LocalSessionStore.create(vertx);
        final var sessionHandler = SessionHandler.create(store).setCookieless(true);
        apiV3Router.route().handler(sessionHandler);

        // Register handlers
        addAuthenticatedPostHandler(apiV3Router, config.getJwtAuthProvider(), MEASUREMENTS_ENDPOINT,
                preRequestMongoAuthHandler, preRequestHandler, failureHandler, preRequestBodyHandler);
        addAuthenticatedPutHandler(apiV3Router, config.getJwtAuthProvider(), MEASUREMENTS_ENDPOINT,
                measurementMongoAuthHandler, measurementHandler, failureHandler);

        // Setup web-api route
        apiV3Router.route().handler(StaticHandler.create("webroot/api/v3"));
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     * 
     * @param router The {@code Router} to register the handler to.
     * @param jwtAuth The {@code JWTAuth} provider to be used for handling the authentication.
     * @param endpoint The URL endpoint to wrap
     * @param mongoAuthHandler The handler which checks the user credentials.
     * @param handler The handler which handles the data.
     * @param failureHandler The handler to add to handle failures.
     * @param bodyHandler The handler to add to handle body size limitations.
     */
    private void addAuthenticatedPostV2Handler(final Router router, final JWTAuth jwtAuth,
            @SuppressWarnings("SameParameterValue") final String endpoint, final MongoAuthHandler mongoAuthHandler,
            final Handler<RoutingContext> handler, final ErrorHandler failureHandler, final BodyHandler bodyHandler) {
        final var jwtAuthHandler = JWTAuthHandler.create(jwtAuth);
        router.post(endpoint)
                .consumes("multipart/form-data")
                .handler(bodyHandler)
                .handler(LoggerHandler.create())
                .handler(jwtAuthHandler)
                .handler(mongoAuthHandler)
                .handler(handler)
                .failureHandler(failureHandler);
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     * 
     * @param router The {@code Router} to register the handler to.
     * @param jwtAuth The {@code JWTAuth} provider to be used for handling the authentication.
     * @param endpoint The URL endpoint to wrap
     * @param mongoAuthHandler The handler which checks the user credentials.
     * @param handler The handler which handles the data.
     * @param failureHandler The handler to add to handle failures.
     * @param bodyHandler The handler to add to handle body size limitations.
     */
    private void addAuthenticatedPostHandler(Router router, JWTAuth jwtAuth,
            @SuppressWarnings("SameParameterValue") final String endpoint, MongoAuthHandler mongoAuthHandler,
            final Handler<RoutingContext> handler,
            final ErrorHandler failureHandler, final BodyHandler bodyHandler) {
        final var jwtHandler = JWTAuthHandler.create(jwtAuth);
        router.post(endpoint)
                .consumes("application/json; charset=UTF-8")
                // Ready request body only once and before async calls or pause/resume must be used see [DAT-749]
                .handler(bodyHandler)
                .handler(LoggerHandler.create())
                .handler(jwtHandler)
                .handler(mongoAuthHandler)
                .handler(handler)
                .failureHandler(failureHandler);
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The {@code Router} to register the handler to.
     * @param jwtAuth The {@code JWTAuth} provider to be used for handling the authentication.
     * @param endpoint The URL endpoint to wrap
     * @param mongoAuthHandler The handler which checks the user credentials.
     * @param handler The handler which handles the data.
     * @param failureHandler The handler to add to handle failures.
     */
    private void addAuthenticatedPutHandler(final Router router, final JWTAuth jwtAuth,
            @SuppressWarnings("SameParameterValue") final String endpoint, final MongoAuthHandler mongoAuthHandler,
            final Handler<RoutingContext> handler,
            final ErrorHandler failureHandler) {
        final var jwtAuthHandler = JWTAuthHandler.create(jwtAuth);
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        router.putWithRegex(String.format("\\%s\\/\\([a-z0-9]{32}\\)\\/", endpoint))
                .consumes("application/octet-stream")
                // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
                .handler(LoggerHandler.create())
                .handler(jwtAuthHandler)
                .handler(mongoAuthHandler)
                .handler(handler)
                .failureHandler(failureHandler);
    }
}
