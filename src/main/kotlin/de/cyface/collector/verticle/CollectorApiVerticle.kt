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
package de.cyface.collector.verticle

import de.cyface.api.Authenticator
import de.cyface.api.FailureHandler
import de.cyface.api.Hasher
import de.cyface.api.HttpServer
import de.cyface.api.Parameter
import de.cyface.api.PauseAndResumeAfterBodyParsing
import de.cyface.api.PauseAndResumeBeforeBodyParsing
import de.cyface.collector.handler.AuthorizationHandler
import de.cyface.collector.handler.MeasurementHandler
import de.cyface.collector.handler.PreRequestHandler
import de.cyface.collector.handler.StatusHandler
import de.cyface.collector.handler.UserCreationHandler
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.gridfs.GridFsStorageService
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.HashingStrategy
import io.vertx.ext.mongo.IndexOptions
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ErrorHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 * @property salt The value to be used as encryption salt
 */
class CollectorApiVerticle(private val salt: String) : AbstractVerticle() {
    @Throws(Exception::class)
    override fun start(startPromise: Promise<Void>) {
        // Load configurations
        val config = Config(vertx)

        // Create indices
        val unique = IndexOptions().unique(true)
        val measurementIndex = JsonObject().put("metadata.deviceId", 1).put("metadata.measurementId", 1)
        // While the db stills contains `v2` data we allow 2 entries per did/mid: fileType:ccyfe & ccyf [DAT-1427]
        measurementIndex.put("metadata.fileType", 1)
        val measurementIndexCreation = config.database.createIndexWithOptions(
            "fs.files",
            measurementIndex,
            unique
        )
        val userIndex = JsonObject().put("username", 1)
        val userIndexCreation = config.database.createIndexWithOptions("user", userIndex, unique)
        val storageService = GridFsStorageService(config.database, vertx.fileSystem())

        // Start http server
        val router = setupRoutes(config, storageService)
        val serverStartPromise = Promise.promise<Void>()
        val httpServer = HttpServer(config.httpPort)
        httpServer.start(vertx, router, serverStartPromise)

        // Insert default admin user
        val adminUsername = Parameter.ADMIN_USER_NAME.stringValue(vertx, "admin")
        val adminPassword = Parameter.ADMIN_PASSWORD.stringValue(vertx, "secret")
        val userCreation = createDefaultUser(config.database, adminUsername, adminPassword)
        storageService.startPeriodicCleaningOfTempData(config.uploadExpirationTime, vertx)
        // Block until all futures completed
        val startUp = CompositeFuture.all(
            userIndexCreation,
            measurementIndexCreation,
            serverStartPromise.future(),
            userCreation
        )
        startUp.onSuccess { startPromise.complete() }
        startUp.onFailure { cause: Throwable? -> startPromise.fail(cause) }
    }

    /**
     * Create an initial default user as specified by the settings.
     *
     * @param mongoClient the Mongo database client used to save the user information.
     * @param adminUsername The username of the default user.
     * @param adminPassword The initial password of the default user.
     */
    private fun createDefaultUser(
        mongoClient: MongoClient,
        adminUsername: String,
        adminPassword: String
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        mongoClient.findOne(
            "user",
            JsonObject().put("username", adminUsername),
            null
        ) { result: AsyncResult<JsonObject?> ->
            if (result.failed()) {
                promise.fail(result.cause())
                return@findOne
            }
            if (result.result() == null) {
                val hasher = Hasher(
                    HashingStrategy.load(),
                    salt.toByteArray(StandardCharsets.UTF_8)
                )
                val userCreationHandler = UserCreationHandler(mongoClient, "user", hasher)
                val userCreation = userCreationHandler.createUser(adminUsername, adminPassword, ADMIN_ROLE)
                userCreation.onSuccess { id: String? ->
                    LOGGER.info("Identifier of new user id: {}", id)
                    promise.complete()
                }
                userCreation.onFailure { e: Throwable? ->
                    LOGGER.error("Unable to create default user!")
                    promise.fail(e)
                }
            } else {
                promise.complete()
            }
        }
        return promise.future()
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param config HTTP server configuration parameters required to set up the routes for the collector API.
     * @param storageService The service used to store the received data.
     * @return the created main `Router`
     */
    private fun setupRoutes(config: Config, storageService: DataStorageService): Router {
        // Setup router
        val mainRouter = Router.router(vertx)
        val apiRouter = Router.router(vertx)
        mainRouter.route(config.endpoint).subRouter(apiRouter)
        setupApiRouter(apiRouter, config, storageService)

        // Setup unknown-resource handler
        mainRouter.route("/*").last().handler(FailureHandler())
        return mainRouter
    }

    /**
     * Sets up the routing for the Cyface collector API.
     *
     * @param apiRouter The sub-router to be used.
     * @param config HTTP server configuration parameters required to set up the routes
     */
    private fun setupApiRouter(apiRouter: Router, config: Config, storageService: DataStorageService) {
        // Setup measurement routes
        val failureHandler = de.cyface.collector.handler.FailureHandler(vertx)

        // Setup authentication
        Authenticator.setupAuthentication("/login", apiRouter, config)

        // Setup session-handler
        // - for all handlers but login and, thus, registering this *after* login
        // - using `cookieless` as our clients pass the session-ID via URI
        val store = LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(store).setCookieless(true)
        apiRouter.route().handler(sessionHandler)

        // Register handlers
        registerPreRequestHandler(apiRouter, config, failureHandler, storageService)
        registerMeasurementHandler(apiRouter, config, failureHandler, storageService)

        // Setup web-api route
        apiRouter.route().handler(StaticHandler.create("webroot/api"))
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param config The configuration parameters used to start the server.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private fun registerPreRequestHandler(
        router: Router,
        config: Config,
        failureHandler: ErrorHandler,
        storageService: DataStorageService
    ) {
        val jwtAuth = config.jwtAuthProvider
        val jwtHandler = JWTAuthHandler.create(jwtAuth)
        val preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE)
        router.post(MEASUREMENTS_ENDPOINT)
            .consumes("application/json; charset=UTF-8")
            .handler(LoggerHandler.create())
            // Read request body only once and before async calls or pause/resume must be used see [DAT-749]
            .handler(preRequestBodyHandler)
            .handler(jwtHandler)
            .handler(AuthorizationHandler(config.authProvider, config.database, PauseAndResumeAfterBodyParsing()))
            .handler(PreRequestHandler(storageService, config.measurementLimit))
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param config The configuration parameters used to start the server.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private fun registerMeasurementHandler(
        router: Router,
        config: Config,
        failureHandler: ErrorHandler,
        storageService: DataStorageService
    ) {
        val jwtAuth = config.jwtAuthProvider
        val jwtAuthHandler = JWTAuthHandler.create(jwtAuth)
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        router.putWithRegex(String.format(Locale.ENGLISH, "\\%s\\/\\([a-z0-9]{32}\\)\\/", MEASUREMENTS_ENDPOINT))
            .consumes("application/octet-stream")
            // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
            .handler(LoggerHandler.create())
            .handler(jwtAuthHandler)
            .handler(AuthorizationHandler(config.authProvider, config.database, PauseAndResumeBeforeBodyParsing()))
            .handler(
                MeasurementHandler(
                    storageService,
                    config.measurementLimit
                )
            )
            .handler(StatusHandler(storageService))
            .failureHandler(failureHandler)
    }

    companion object {
        /**
         * The `Logger` used for objects of this class. Configure it by changing the settings in
         * `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(CollectorApiVerticle::class.java)

        /**
         * The role which identifies users with "admin" privileges.
         */
        private const val ADMIN_ROLE = "admin"

        /**
         * The endpoint which accepts measurement uploads.
         */
        private const val MEASUREMENTS_ENDPOINT = "/measurements"

        /**
         * The number of bytes in one kilobyte. This can be used to limit the amount of data accepted by the server.
         */
        private const val BYTES_IN_ONE_KILOBYTE = 1024L
    }
}
