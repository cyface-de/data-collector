/*
 * Copyright 2018-2023 Cyface GmbH
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

import de.cyface.collector.auth.MockedHandlerBuilder
import de.cyface.collector.auth.OAuth2HandlerBuilder
import de.cyface.collector.configuration.AuthType
import de.cyface.collector.configuration.Configuration
import de.cyface.collector.handler.AuthorizationHandler
import de.cyface.collector.handler.FailureHandler
import de.cyface.collector.handler.MeasurementHandler
import de.cyface.collector.handler.PreRequestHandler
import de.cyface.collector.handler.StatusHandler
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ErrorHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.ext.web.handler.OAuth2AuthHandler
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
 * @version 2.3.0
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

            val routerSetup = setupRoutes(storageService)
            val httpServer = HttpServer(config.serviceHttpAddress.port)
            routerSetup.onSuccess { httpServer.start(vertx, it, serverStartPromise) }
            routerSetup.onFailure { startPromise.fail(it) }
        }

        // Block until future completed
        serverStartPromise.future().onSuccess {
            LOGGER.info("Successfully started collector API!")
            startPromise.complete()
        }
        serverStartPromise.future().onFailure { cause: Throwable? ->
            LOGGER.error("Failed to start collector API!", cause)
            startPromise.fail(cause)
        }
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param storageService The service used to store the received data.
     * @return the created main `Router`
     */
    private fun setupRoutes(storageService: DataStorageService): Future<Router> {
        // Setup router
        val mainRouter = Router.router(vertx)
        val promise = Promise.promise<Router>()

        // API
        val apiRouter = Router.router(vertx)
        val mainRoutePath = config.serviceHttpAddress.path
        println("Starting Collector Server on: $mainRoutePath")
        mainRouter
            .route(mainRoutePath)
            .handler(LoggerHandler.create())
            .subRouter(apiRouter)
        setupApiRouter(apiRouter, storageService)
            .onSuccess { promise.complete(mainRouter) }
            .onFailure { promise.fail(it) }

        // Setup unknown-resource handler
        mainRouter.route("/*").last().handler(FailureHandler(vertx))
        return promise.future()
    }

    /**
     * Sets up the routing for the Cyface collector API.
     *
     * @param apiRouter The sub-router to be used.
     * @param storageService The service used to store and retrieve data. This is required by the handlers receiving
     * data to store that data.
     */
    private fun setupApiRouter(
        apiRouter: Router,
        storageService: DataStorageService
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        val failureHandler = FailureHandler(vertx)

        // Setup session-handler
        // - using `cookieless` as our clients pass the session-ID via URI
        val store = LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(store).setCookieless(true)
        apiRouter.route().handler(sessionHandler)

        // Setup OAuth2 discovery and callback route for token introspection (authentication)
        val options = OAuth2Options()
            .setClientId(config.oauthClient)
            .setClientSecret(config.oauthSecret)
            .setSite(config.oauthSite.toString())
            .setTenant(config.oauthTenant)
        val authBuilder = if (config.authType == AuthType.Mocked) {
            MockedHandlerBuilder()
        } else {
            OAuth2HandlerBuilder(
                vertx,
                apiRouter,
                config.oauthCallback,
                options
            )
        }
        authBuilder.create()
            .onSuccess {
                // Register handlers which require authentication
                val preRequestHandlerAuthorizationHandler = AuthorizationHandler()
                registerPreRequestHandler(
                    apiRouter,
                    it,
                    preRequestHandlerAuthorizationHandler,
                    failureHandler,
                    storageService
                )
                val measurementRequestAuthorizationHandler = AuthorizationHandler()
                registerMeasurementHandler(
                    apiRouter,
                    it,
                    measurementRequestAuthorizationHandler,
                    failureHandler,
                    storageService
                )

                promise.complete()
            }
            .onFailure { promise.fail(it) }

        // Setup web-api route
        apiRouter.route().handler(StaticHandler.create("webroot/api"))

        return promise.future()
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param oauth2Handler The handler to authenticate the user using an OAuth2 endpoint.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private fun registerPreRequestHandler(
        router: Router,
        oauth2Handler: OAuth2AuthHandler,
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
            .handler(oauth2Handler)
            .handler(authorizationHandler)
            .handler(PreRequestHandler(storageService, config.measurementPayloadLimit))
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param oauth2Handler The handler to authenticate the user using an OAuth2 endpoint.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param storageService Service used to write the received data.
     */
    private fun registerMeasurementHandler(
        router: Router,
        oauth2Handler: OAuth2AuthHandler,
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
            .handler(oauth2Handler)
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
         * The endpoint which accepts measurement uploads.
         */
        private const val MEASUREMENTS_ENDPOINT = "/measurements"

        /**
         * The number of bytes in one kilobyte. This can be used to limit the amount of data accepted by the server.
         */
        const val BYTES_IN_ONE_KILOBYTE = 1024L
    }
}
