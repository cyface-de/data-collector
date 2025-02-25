/*
 * Copyright 2018-2025 Cyface GmbH
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
import de.cyface.collector.handler.upload.PreRequestHandler
import de.cyface.collector.handler.upload.StatusHandler
import de.cyface.collector.handler.upload.UploadHandler
import de.cyface.collector.model.AttachmentFactory
import de.cyface.collector.model.MeasurementFactory
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.DataStorageServiceBuilder
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.AuthenticationHandler
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ErrorHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
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
 * @property storageServiceBuilder A builder for a DataStorageService, responsible for storing the submitted
 * information.
 */
class CollectorApiVerticle(
    private val authHandlerBuilder: AuthHandlerBuilder,
    private val serverConfiguration: ServerConfiguration,
    private val storageServiceBuilder: DataStorageServiceBuilder,
) : CoroutineVerticle() {

    /**
     * Logger used by objects of this class. Configure it using "src/main/resources/logback.xml".
     */
    private val logger: Logger = LoggerFactory.getLogger(CollectorApiVerticle::class.java)

    /**
     * Creates [de.cyface.collector.model.Measurement] instances from the raw data received by the API.
     */
    private val measurementFactory = MeasurementFactory()

    /**
     * Creates [de.cyface.collector.model.Attachment] instances from the raw data received by the API.
     */
    private val attachmentFactory = AttachmentFactory()

    @Throws(Exception::class)
    override suspend fun start() {
        try {
            logger.info("Starting collector API!")

            // Start http server
            val storageService = storageServiceBuilder.create().coAwait()
            logger.info("Created storage service.")
            val uploadExpirationTime = serverConfiguration.uploadExpirationTimeInMillis
            // TODO What does this mean?
            logger.info("Requests the storage service expire after $uploadExpirationTime milliseconds.")
            val cleanUpOperation = storageServiceBuilder.createCleanupOperation()
            storageService.startPeriodicCleaningOfTempData(uploadExpirationTime, vertx, cleanUpOperation)

            val router = setupRoutes(storageService)
            val httpServer = HttpServer(serverConfiguration.port)
            httpServer.start(vertx, router)

            logger.info("Successfully started collector API!")
        } catch(e: Throwable) {
            logger.error("Failed to start collector API!", e)
        }
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param storageService The service used to store the received data.
     * @return the created main [Router]
     */
    private suspend fun setupRoutes(
        storageService: DataStorageService,
    ): Router {
        // Setup router
        val router = Router.router(vertx)

        // API
        setupApiRouter(router, storageService)

        // Setup unknown-resource handler
        router.route("/*").last().handler(FailureHandler(vertx))
        return router
    }

    /**
     * Sets up the routing for the Cyface collector API.
     *
     * @param apiRouter A router hosting the collector API.
     * @param storageService The service used to store and retrieve data. This is required by the handlers receiving
     * data to store that data.
     */
    private suspend fun setupApiRouter(
        apiRouter: Router,
        storageService: DataStorageService,
    ) {
        val failureHandler = FailureHandler(vertx)

        // Setup session-handler
        // - using `cookieless` as our clients pass the session-ID via URI
        val store = LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(store).setCookieless(true)
        apiRouter.route().handler(sessionHandler)

        // Setup OAuth2 discovery and callback route for token introspection (authentication)
        val authHandler = authHandlerBuilder.create(apiRouter)
        // Register handlers which require authentication
        val authorizationHandler = AuthorizationHandler()
        registerMeasurementHandlers(storageService, apiRouter, authHandler, authorizationHandler, failureHandler)
        // Register attachment endpoints after the measurement endpoints to ensure they can be distinguished
        // by the router. The measurement endpoint is:
        // - /measurements/(sessionId)
        // The attachment endpoint, a subdirectory of the provider measurement endpoint, is:
        // - /measurements/:did/:mid/attachments/(sessionId)
        registerAttachmentHandlers(storageService, apiRouter, authHandler, authorizationHandler, failureHandler)

        // Setup web-api route
        apiRouter.route().handler(StaticHandler.create("webroot/api"))
    }

    private fun registerMeasurementHandlers(
        storageService: DataStorageService,
        apiRouter: Router,
        oAuth2AuthHandler: AuthenticationHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: FailureHandler
    ) {
        val measurementPreRequestHandler = PreRequestHandler(
            measurementFactory,
            storageService,
            serverConfiguration.measurementPayloadLimit,
            serverConfiguration.httpEndpoint
        )
        registerMeasurementPreRequestHandler(
            apiRouter,
            oAuth2AuthHandler,
            authorizationHandler,
            failureHandler,
            measurementPreRequestHandler,
        )
        val measurementUploadHandler = UploadHandler(
            measurementFactory,
            storageService,
            serverConfiguration.measurementPayloadLimit,
        )
        val measurementStatusHandler = StatusHandler(
            measurementFactory,
            storageService,
        )
        registerMeasurementUploadHandler(
            apiRouter,
            oAuth2AuthHandler,
            authorizationHandler,
            failureHandler,
            measurementUploadHandler,
            measurementStatusHandler,
        )
    }

    private fun registerAttachmentHandlers(
        storageService: DataStorageService,
        apiRouter: Router,
        oAuth2AuthHandler: AuthenticationHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: FailureHandler
    ) {
        val attachmentPreRequestHandler = PreRequestHandler(
            attachmentFactory,
            storageService,
            serverConfiguration.measurementPayloadLimit,
            serverConfiguration.httpEndpoint
        )
        registerAttachmentPreRequestHandler(
            apiRouter,
            oAuth2AuthHandler,
            authorizationHandler,
            failureHandler,
            attachmentPreRequestHandler,
        )
        val attachmentUploadHandler = UploadHandler(
            attachmentFactory,
            storageService,
            serverConfiguration.measurementPayloadLimit,
        )
        val attachmentStatusHandler = StatusHandler(
            attachmentFactory,
            storageService,
        )
        registerAttachmentUploadHandler(
            apiRouter,
            oAuth2AuthHandler,
            authorizationHandler,
            failureHandler,
            attachmentUploadHandler,
            attachmentStatusHandler,
        )
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param authenticationHandler The handler to authenticate the user.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param requestHandler The actual handler for the received HTTP request.
     */
    private fun registerMeasurementPreRequestHandler(
        router: Router,
        authenticationHandler: AuthenticationHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        requestHandler: PreRequestHandler,
    ) {
        val preRequestBodyHandler = BodyHandler.create().setBodyLimit(BYTES_IN_ONE_KILOBYTE)
        router.post(MEASUREMENTS_ENDPOINT)
            .consumes("application/json; charset=UTF-8")
            // Read request body only once and before async calls or pause/resume must be used see [DAT-749]
            .handler(preRequestBodyHandler)
            .handler(authenticationHandler)
            .handler(authorizationHandler)
            .handler(requestHandler)
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param authenticationHandler The handler to authenticate the user.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param requestHandler The actual handler for upload requests.
     * @param statusHandler The actual handler for status requests.
     */
    private fun registerMeasurementUploadHandler(
        router: Router,
        authenticationHandler: AuthenticationHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        requestHandler: UploadHandler,
        statusHandler: StatusHandler,
    ) {
        // The path pattern ../(sid)/.. was chosen because of the documentation of Vert.X SessionHandler
        // https://vertx.io/docs/vertx-web/java/#_handling_sessions
        router.putWithRegex(String.format(Locale.ENGLISH, "\\%s\\/\\([a-z0-9]{32}\\)\\/", MEASUREMENTS_ENDPOINT))
            .consumes("application/octet-stream")
            // Not using BodyHandler as the `request.body()` can only be read once and the {@code #handler} does so.
            .handler(authenticationHandler)
            .handler(authorizationHandler)
            .handler(requestHandler)
            .handler(statusHandler)
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param authenticationHandler The handler to authenticate the user.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param requestHandler The actual handler for the received HTTP request.
     */
    private fun registerAttachmentPreRequestHandler(
        router: Router,
        authenticationHandler: AuthenticationHandler,
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
            .handler(authenticationHandler)
            .handler(authorizationHandler)
            .handler(requestHandler)
            .failureHandler(failureHandler)
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param router The `Router` to register the handler to.
     * @param authenticationHandler The handler to authenticate the user.
     * @param authorizationHandler The handler used to authorize a user after successful authentication.
     * @param failureHandler The handler to add to handle failures.
     * @param requestHandler The actual handler for upload requests.
     * @param statusHandler The actual handler for status requests.
     */
    private fun registerAttachmentUploadHandler(
        router: Router,
        authenticationHandler: AuthenticationHandler,
        authorizationHandler: AuthorizationHandler,
        failureHandler: ErrorHandler,
        requestHandler: UploadHandler,
        statusHandler: StatusHandler,
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
            .handler(authenticationHandler)
            .handler(authorizationHandler)
            .handler(requestHandler)
            .handler(statusHandler)
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
