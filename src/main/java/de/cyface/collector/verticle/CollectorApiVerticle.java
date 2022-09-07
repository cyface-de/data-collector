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

import java.nio.charset.StandardCharsets;

import de.cyface.api.PauseAndResumeAfterBodyParsing;
import de.cyface.api.PauseAndResumeBeforeBodyParsing;
import de.cyface.collector.handler.AuthorizationHandler;
import de.cyface.collector.handler.FailureHandler;
import de.cyface.collector.handler.MeasurementHandler;
import de.cyface.collector.handler.StatusHandler;
import de.cyface.collector.storage.DataStorageService;
import de.cyface.collector.storage.GridFsStorageService;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.api.Authenticator;
import de.cyface.api.Hasher;
import de.cyface.api.Parameter;
import de.cyface.collector.handler.PreRequestHandler;
import de.cyface.collector.handler.UserCreationHandler;
import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
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
     */
    public CollectorApiVerticle(final String salt) {
        super();
        this.salt = salt;
    }

    @Override
    public void start(final Promise<Void> startPromise) throws Exception {
        Validate.notNull(startPromise);

        // Setup Measurement event bus
        prepareEventBus();

        // Load configurations
        final var config = new Config(vertx);

        // Create indices
        final var unique = new IndexOptions().unique(true);
        final var measurementIndex = new JsonObject().put("metadata.deviceId", 1).put("metadata.measurementId", 1);
        // While the db stills contains `v2` data we allow 2 entries per did/mid: fileType:ccyfe & ccyf [DAT-1427]
        measurementIndex.put("metadata.fileType", 1);
        final var measurementIndexCreation = config.getDatabase().createIndexWithOptions("fs.files",
                measurementIndex, unique);
        final var userIndex = new JsonObject().put("username", 1);
        final var userIndexCreation = config.getDatabase().createIndexWithOptions("user", userIndex, unique);
        final var storageService = new GridFsStorageService(config.getDatabase(), vertx.fileSystem());

        // Start http server
        final var router = setupRoutes(config, storageService);
        final Promise<Void> serverStartPromise = Promise.promise();
        final var httpServer = new de.cyface.api.HttpServer(config.getHttpPort());
        httpServer.start(vertx, router, serverStartPromise);

        // Insert default admin user
        final var adminUsername = Parameter.ADMIN_USER_NAME.stringValue(vertx, "admin");
        final var adminPassword = Parameter.ADMIN_PASSWORD.stringValue(vertx, "secret");
        final var userCreation = createDefaultUser(config.getDatabase(), adminUsername, adminPassword);

        storageService.startPeriodicCleaningOfTempData(config.getUploadExpirationTime(), vertx);
        // Block until all futures completed
        final var startUp = CompositeFuture.all(
                userIndexCreation,
                measurementIndexCreation,
                serverStartPromise.future(),
                userCreation);
        startUp.onSuccess(success -> startPromise.complete());
        startUp.onFailure(startPromise::fail);
    }

    private Future<Void> createDefaultUser(final MongoClient mongoClient, final String adminUsername,
            final String adminPassword) {

        final Promise<Void> promise = Promise.promise();
        mongoClient.findOne("user", new JsonObject().put("username", adminUsername), null, result -> {
            if (result.failed()) {
                promise.fail(result.cause());
                return;
            }
            if (result.result() == null) {
                final var hasher = new Hasher(HashingStrategy.load(),
                        salt.getBytes(StandardCharsets.UTF_8));
                final var userCreationHandler = new UserCreationHandler(mongoClient, "user", hasher);
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
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param config HTTP server configuration parameters required to set up the routes for the collector API.
     * @param storageService The service used to store the received data.
     * @return the created main {@code Router}
     */
    private Router setupRoutes(final Config config, final DataStorageService storageService) {

        // Setup router
        final var mainRouter = Router.router(vertx);
        final var apiRouter = Router.router(vertx);

        mainRouter.route(config.getEndpoint()).subRouter(apiRouter);
        setupApiRouter(apiRouter, config, storageService);

        // Setup unknown-resource handler
        mainRouter.route("/*").last().handler(new de.cyface.api.FailureHandler());

        return mainRouter;
    }

    /**
     * Sets up the routing for the Cyface collector API.
     *
     * @param apiRouter The sub-router to be used.
     * @param config HTTP server configuration parameters required to set up the routes
     */
    private void setupApiRouter(final Router apiRouter, final Config config, final DataStorageService storageService) {

        // Setup measurement routes
        final var failureHandler = new FailureHandler(vertx);

        // Setup authentication
        Authenticator.setupAuthentication("/login", apiRouter, config);

        // Setup session-handler
        // - for all handlers but login and, thus, registering this *after* login
        // - using `cookieless` as our clients pass the session-ID via URI
        final var store = LocalSessionStore.create(vertx);
        final var sessionHandler = SessionHandler.create(store).setCookieless(true);
        apiRouter.route().handler(sessionHandler);

        // Register handlers
        registerPreRequestHandler(apiRouter, config, failureHandler, storageService);
        registerMeasurementHandler(apiRouter, config, failureHandler, storageService);

        // Setup web-api route
        apiRouter.route().handler(StaticHandler.create("webroot/api"));
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     * 
     * @param router The {@code Router} to register the handler to.
     * @param config The configuration parameters used to start the server.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private void registerPreRequestHandler(final Router router,
                                           final Config config,
                                           final ErrorHandler failureHandler,
                                           final DataStorageService storageService) {

        final var jwtAuth = config.getJwtAuthProvider();
        final var jwtHandler = JWTAuthHandler.create(jwtAuth);
        final var preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE);
        router.post(MEASUREMENTS_ENDPOINT)
                .consumes("application/json; charset=UTF-8")
                .handler(LoggerHandler.create())
                // Ready request body only once and before async calls or pause/resume must be used see [DAT-749]
                .handler(preRequestBodyHandler)
                .handler(jwtHandler)
                .handler(new AuthorizationHandler(config.getAuthProvider(), config.getDatabase(), new PauseAndResumeAfterBodyParsing()))
                .handler(new PreRequestHandler(storageService, config.getMeasurementLimit()))
                .failureHandler(failureHandler);
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The {@code Router} to register the handler to.
     * @param config The configuration parameters used to start the server.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private void registerMeasurementHandler(
            final Router router,
            final Config config,
            final ErrorHandler failureHandler,
            final DataStorageService storageService) {

        final var jwtAuth = config.getJwtAuthProvider();
        final var jwtAuthHandler = JWTAuthHandler.create(jwtAuth);
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        router.putWithRegex(String.format("\\%s\\/\\([a-z0-9]{32}\\)\\/", MEASUREMENTS_ENDPOINT))
                .consumes("application/octet-stream")
                // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
                .handler(LoggerHandler.create())
                .handler(jwtAuthHandler)
                .handler(new AuthorizationHandler(config.getAuthProvider(), config.getDatabase(), new PauseAndResumeBeforeBodyParsing()))
                .handler(new MeasurementHandler(
                        storageService,
                        config.getMeasurementLimit()))
                .handler(new StatusHandler(config.getDatabase()))
                .failureHandler(failureHandler);
    }
}
