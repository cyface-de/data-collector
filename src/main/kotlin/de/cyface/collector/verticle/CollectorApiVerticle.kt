/*
 * Copyright 2018-2024 Cyface GmbH
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

import de.cyface.collector.auth.AuthHandlerBuilder
import de.cyface.collector.handler.AuthorizationHandler
import de.cyface.collector.handler.FailureHandler
import de.cyface.collector.handler.PreRequestHandler
import de.cyface.collector.handler.StatusHandler
import de.cyface.collector.handler.UploadHandler
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ErrorHandler
import io.vertx.ext.web.handler.OAuth2AuthHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @property authHandlerBuilder Create a handler for authentication requests.
 * @property serverConfiguration The configuration used by this `CollectorApiVerticle`.
 * @property mongoDatabaseConfiguration Configuration of a mongo database to store sensor data and meta data
 * for a measurement.
 */
class CollectorApiVerticle(
    private val authHandlerBuilder: AuthHandlerBuilder,
    private val serverConfiguration: ServerConfiguration,
    private val mongoDatabaseConfiguration: JsonObject
) : AbstractVerticle() {

    /**
     * Logger used by objects of this class. Configure it using "src/main/resources/logback.xml".
     */
    val logger: Logger = LoggerFactory.getLogger(CollectorApiVerticle::class.java)

    @Throws(Exception::class)
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting collector API!")

        // Start http server

        val mongoClient = MongoClient.createShared(
            vertx,
            mongoDatabaseConfiguration,
            mongoDatabaseConfiguration.getString("data_source_name")
        )
        val storageServiceBuilder = serverConfiguration.largeFileStorageType.dataStorageServiceBuilder(
            vertx,
            mongoClient
        )
        val storageServiceBuilderCall = storageServiceBuilder.create()
        val serverStartPromise = Promise.promise<Void>()
        storageServiceBuilderCall.onSuccess { storageService ->
            logger.info("Created storage service.")
            val uploadExpirationTime = serverConfiguration.uploadExpirationTimeInMillis
            logger.info("Requests to the storage service expire after $uploadExpirationTime milliseconds.")
            val cleanUpOperation = storageServiceBuilder.createCleanupOperation()
            storageService.startPeriodicCleaningOfTempData(uploadExpirationTime, vertx, cleanUpOperation)

            try {
                val routerSetup = setupRoutes(storageService)
                val httpServer = HttpServer(serverConfiguration.port)
                routerSetup.onSuccess { httpServer.start(vertx, it, serverStartPromise) }
                routerSetup.onFailure { serverStartPromise.fail(it) }
            } catch (e: IllegalStateException) {
                serverStartPromise.fail(e)
            }
        }.onFailure {
            serverStartPromise.fail(it)
        }

        // Block until future completed
        serverStartPromise.future().onSuccess {
            logger.info("Successfully started collector API!")
            startPromise.complete()
        }
        serverStartPromise.future().onFailure { cause: Throwable? ->
            logger.error("Failed to start collector API!", cause)
            startPromise.fail(cause)
        }
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param storageService The service used to store the received data.
     * @return the created main `Router`
     */
    private fun setupRoutes(
        storageService: DataStorageService,
    ): Future<Router> {
        // Setup router
        val router = Router.router(vertx)
        val promise = Promise.promise<Router>()

        // API
        setupApiRouter(router, storageService)
            .onSuccess { promise.complete(router) }
            .onFailure { promise.fail(it) }

        // Setup unknown-resource handler
        router.route("/*").last().handler(FailureHandler(vertx))
        return promise.future()
    }

    /**
     * Sets up the routing for the Cyface collector API.
     *
     * @param apiRouter A router hosting the collector API.
     * @param storageService The service used to store and retrieve data. This is required by the handlers receiving
     * data to store that data.
     */
    private fun setupApiRouter(
        apiRouter: Router,
        storageService: DataStorageService,
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        val failureHandler = FailureHandler(vertx)

        // Setup session-handler
        // - using `cookieless` as our clients pass the session-ID via URI
        val store = LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(store).setCookieless(true)
        apiRouter.route().handler(sessionHandler)

        // Setup OAuth2 discovery and callback route for token introspection (authentication)
        authHandlerBuilder.create(apiRouter)
            .onSuccess {
                // Register handlers which require authentication
                val authorizationHandler = AuthorizationHandler()
                val preRequestHandler = PreRequestHandler(
                    storageService,
                    serverConfiguration.measurementPayloadLimit,
                    serverConfiguration.httpEndpoint
                )
                registerMeasurementPreRequestHandler(
                    apiRouter,
                    it,
                    authorizationHandler,
                    failureHandler,
                    preRequestHandler,
                )
                registerMeasurementUploadHandler(
                    apiRouter,
                    it,
                    authorizationHandler,
                    failureHandler,
                    storageService,
                )
                // Register attachment endpoints after the measurement endpoints to ensure they can be distinguished
                // by the router. The measurement endpoint is:
                // - /measurements/(sessionId)
                // The attachment endpoint, a subdirectory of the provider measurement endpoint, is:
                // - /measurements/:did/:mid/attachments/(sessionId)
                registerAttachmentPreRequestHandler(
                    apiRouter,
                    it,
                    authorizationHandler,
                    failureHandler,
                    preRequestHandler,
                )
                registerAttachmentUploadHandler(
                    apiRouter,
                    it,
                    authorizationHandler,
                    failureHandler,
                    storageService,
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
     * @param requestHandler The actual handler for the received HTTP request.
     */
    private fun registerMeasurementPreRequestHandler(
        router: Router,
        oauth2Handler: OAuth2AuthHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        requestHandler: PreRequestHandler,
    ) {
        val preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE)
        router.post(MEASUREMENTS_ENDPOINT)
            .consumes("application/json; charset=UTF-8")
            // Read request body only once and before async calls or pause/resume must be used see [DAT-749]
            .handler(preRequestBodyHandler)
            .handler(oauth2Handler)
            .handler(authorizationHandler)
            .handler(requestHandler)
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
    private fun registerMeasurementUploadHandler(
        router: Router,
        oauth2Handler: OAuth2AuthHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        storageService: DataStorageService,
    ) {
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        router.putWithRegex(String.format(Locale.ENGLISH, "\\%s\\/\\([a-z0-9]{32}\\)\\/", MEASUREMENTS_ENDPOINT))
            .consumes("application/octet-stream")
            // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
            .handler(oauth2Handler)
            .handler(authorizationHandler)
            .handler(UploadHandler(storageService, serverConfiguration.measurementPayloadLimit))
            .handler(StatusHandler(storageService))
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param oauth2Handler The handler to authenticate the user using an OAuth2 endpoint.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param requestHandler The actual handler for the received HTTP request.
     */
    private fun registerAttachmentPreRequestHandler(
        router: Router,
        oauth2Handler: OAuth2AuthHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        requestHandler: PreRequestHandler,
    ) {
        val preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE)
        router.postWithRegex(
            String.format(
                Locale.ENGLISH,
                // e.g. /measurements/00000000-0000-0000-0000-000000000000/1234567890/attachments
                "%s/([a-fA-F0-9\\-]{36})/([0-9]{1,19})%s",
                MEASUREMENTS_ENDPOINT,
                ATTACHMENTS_ENDPOINT
            )
        )
            .consumes("application/json; charset=UTF-8")
            // Read request body only once and before async calls or pause/resume must be used see [DAT-749]
            .handler(preRequestBodyHandler)
            .handler(oauth2Handler)
            .handler(authorizationHandler)
            .handler(requestHandler)
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
    private fun registerAttachmentUploadHandler(
        router: Router,
        oauth2Handler: OAuth2AuthHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        storageService: DataStorageService,
    ) {
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        router.putWithRegex(
            String.format(
                Locale.ENGLISH,
                // e.g. /measurements/00000000-0000-0000-0000-000000000000/1234567890/attachments/(sid)
                "%s/([a-fA-F0-9\\-]{36})/([0-9]{1,19})%s/\\([a-z0-9]{32}\\)/",
                MEASUREMENTS_ENDPOINT,
                ATTACHMENTS_ENDPOINT
            )
        )
            .consumes("application/octet-stream")
            // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
            .handler(oauth2Handler)
            .handler(authorizationHandler)
            .handler(UploadHandler(storageService, serverConfiguration.measurementPayloadLimit))
            .handler(StatusHandler(storageService))
            .failureHandler(failureHandler)
    }

    companion object {
        /**
         * The endpoint which accepts measurement uploads.
         */
        private const val MEASUREMENTS_ENDPOINT = "/measurements"
        /**
         * The endpoint which accepts measurement uploads.
         */
        private const val ATTACHMENTS_ENDPOINT = "/attachments"

        /**
         * The number of bytes in one kilobyte. This can be used to limit the amount of data accepted by the server.
         */
        const val BYTES_IN_ONE_KILOBYTE = 1024L
    }
}
