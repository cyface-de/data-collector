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

import de.cyface.api.FailureHandler
import de.cyface.api.Hasher
import de.cyface.api.HttpServer
import de.cyface.api.PauseAndResumeAfterBodyParsing
import de.cyface.api.PauseAndResumeBeforeBodyParsing
import de.cyface.collector.configuration.Configuration
import de.cyface.collector.handler.AuthorizationHandler
import de.cyface.collector.handler.MeasurementHandler
import de.cyface.collector.handler.PreRequestHandler
import de.cyface.collector.handler.StatusHandler
import de.cyface.collector.handler.UserCreationHandler
import de.cyface.collector.handler.auth.AuthenticationConfiguration
import de.cyface.collector.handler.auth.Authenticator
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.HashingStrategy
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
import java.util.Locale

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.0
 * @since 2.0.0
 * @property config The configuration used for the verticle.
 */
class CollectorApiVerticle(
    private val config: Configuration,
) : AbstractVerticle() {
    @Throws(Exception::class)
    override fun start(startPromise: Promise<Void>) {
        LOGGER.info("Starting collector API!")

        // Start http server

        val mongoClient = MongoClient.createShared(
            vertx,
            config.mongoDb,
            config.mongoDb.getString("data_source_name")
        )
        val storageServiceBuilder = config.storageType.dataStorageServiceBuilder(vertx, mongoClient)
        val storageServiceBuilderCall = storageServiceBuilder.create()
        val serverStartPromise = Promise.promise<Void>()
        storageServiceBuilderCall.onSuccess { storageService ->
            LOGGER.info("Created storage service.")
            val uploadExpirationTime = config.uploadExpiration
            LOGGER.info("Requests to the storage service expire after $uploadExpirationTime milliseconds.")
            val cleanUpOperation = storageServiceBuilder.createCleanupOperation()
            storageService.startPeriodicCleaningOfTempData(uploadExpirationTime, vertx, cleanUpOperation)

            val router = setupRoutes(storageService, mongoClient)
            val httpServer = HttpServer(config.serviceHttpAddress.port)
            httpServer.start(vertx, router, serverStartPromise)
        }

        // Insert default admin user
        val userCreation = createDefaultUser(mongoClient, config.adminUser, config.adminPassword)

        // Block until all futures completed
        val startUp = CompositeFuture.all(
            serverStartPromise.future(),
            userCreation
        )
        startUp.onSuccess {
            LOGGER.info("Successfully started collector API!")
            startPromise.complete()
        }
        startUp.onFailure { cause: Throwable? ->
            LOGGER.error("Failed to start collector API!", cause)
            startPromise.fail(cause)
        }
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
        LOGGER.info("Creating default user $adminUsername.")
        val promise = Promise.promise<Void>()

        val findDefaultUserCall = mongoClient.findOne(
            "user",
            JsonObject().put("username", adminUsername),
            null
        )
        val loadSaltCall = config.salt.bytes(vertx)

        val loadSaltAndfindDefaultUserCall = CompositeFuture.all(findDefaultUserCall, loadSaltCall)
        loadSaltAndfindDefaultUserCall.onSuccess { result ->
            LOGGER.info("Finished searching for admin user $adminUsername.")
            val foundUser = result.resultAt<JsonObject?>(0)
            val salt = result.resultAt<ByteArray>(1)
            if (foundUser == null) {
                val hasher = Hasher(HashingStrategy.load(), salt)
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
        loadSaltAndfindDefaultUserCall.onFailure(promise::fail)
        return promise.future()
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param storageService The service used to store the received data.
     * @param mongoClient The client for the Mongo database storing the user authentication and authorization
     * information.
     * @return the created main `Router`
     */
    private fun setupRoutes(storageService: DataStorageService, mongoClient: MongoClient): Router {
        // Setup router
        val mainRouter = Router.router(vertx)
        val apiRouter = Router.router(vertx)
        val mainRoutePath = config.serviceHttpAddress.path
        println("Starting Collector Server on: $mainRoutePath")
        mainRouter
            .route(mainRoutePath)
            .handler(LoggerHandler.create())
            .subRouter(apiRouter)
        setupApiRouter(apiRouter, storageService, mongoClient)

        // Setup unknown-resource handler
        mainRouter.route("/*").last().handler(FailureHandler())
        return mainRouter
    }

    /**
     * Sets up the routing for the Cyface collector API.
     *
     * @param apiRouter The sub-router to be used.
     * @param storageService The service used to store and retreive data. This is required by the handlers receiving
     * data to store that data.
     * @param mongoClient Provide access to the database storing authentication and authorization information.
     */
    private fun setupApiRouter(apiRouter: Router, storageService: DataStorageService, mongoClient: MongoClient) {
        // Setup measurement routes
        val failureHandler = de.cyface.collector.handler.FailureHandler(vertx)

        // Setup authentication
        val authenticationConfiguration = AuthenticationConfiguration(config, vertx, mongoClient)
        Authenticator.setupAuthentication("/login", apiRouter, authenticationConfiguration)

        // Setup session-handler
        // - for all handlers but login and, thus, registering this *after* login
        // - using `cookieless` as our clients pass the session-ID via URI
        val store = LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(store).setCookieless(true)
        apiRouter.route().handler(sessionHandler)

        // Register handlers
        val jwtAuth = authenticationConfiguration.jwtAuthProvider
        val authProvider = authenticationConfiguration.authProvider
        val jwtHandler = JWTAuthHandler.create(jwtAuth)
        val preRequestHandlerAuthorizationhandler = AuthorizationHandler(
            authProvider,
            mongoClient,
            PauseAndResumeAfterBodyParsing()
        )
        registerPreRequestHandler(
            apiRouter,
            jwtHandler,
            preRequestHandlerAuthorizationhandler,
            failureHandler,
            storageService
        )
        val measurementRequestAuthorizationHandler = AuthorizationHandler(
            authProvider,
            mongoClient,
            PauseAndResumeBeforeBodyParsing()
        )
        registerMeasurementHandler(
            apiRouter,
            jwtHandler,
            measurementRequestAuthorizationHandler,
            failureHandler,
            storageService
        )

        // Setup web-api route
        apiRouter.route().handler(StaticHandler.create("webroot/api"))
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param jwtAuthHandler The handler to authenticate the user using JWT tokens.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private fun registerPreRequestHandler(
        router: Router,
        jwtAuthHandler: JWTAuthHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        storageService: DataStorageService
    ) {
        val preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE)
        router.post(MEASUREMENTS_ENDPOINT)
            .consumes("application/json; charset=UTF-8")
            .handler(LoggerHandler.create())
            // Read request body only once and before async calls or pause/resume must be used see [DAT-749]
            .handler(preRequestBodyHandler)
            .handler(jwtAuthHandler)
            .handler(authorizationHandler)
            .handler(PreRequestHandler(storageService, config.measurementPayloadLimit))
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param jwtAuthHandler The handler to authenticate the user using JWT tokens.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private fun registerMeasurementHandler(
        router: Router,
        jwtAuthHandler: JWTAuthHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        storageService: DataStorageService
    ) {
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        val measurementHandler = config.measurementPayloadLimit
        router.putWithRegex(String.format(Locale.ENGLISH, "\\%s\\/\\([a-z0-9]{32}\\)\\/", MEASUREMENTS_ENDPOINT))
            .consumes("application/octet-stream")
            // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
            .handler(LoggerHandler.create())
            .handler(jwtAuthHandler)
            .handler(authorizationHandler)
            .handler(MeasurementHandler(storageService, measurementHandler))
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
