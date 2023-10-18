/*
 * Copyright 2020-2023 Cyface GmbH
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

import io.vertx.core.AsyncResult
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory

/**
 * A wrapper which starts the `HttpServer` for [CollectorApiVerticle].
 *
 * @author Armin Schnabel
 * @version 1.0.3
 * @since 7.1.0
 * @property port The port on which the HTTP server should listen
 */
class HttpServer(
    private val port: Int
) {
    /**
     * Starts the HTTP server provided by this application. This server runs the Cyface Collector REST-API.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @param router The router for all the endpoints the HTTP server should serve
     * @param startPromise Informs the caller about the successful or failed start of the server
     */
    fun start(vertx: Vertx, router: Router, startPromise: Promise<Void>) {
        Validate.notNull(router)
        Validate.notNull(startPromise)
        val options = HttpServerOptions()
        options.isCompressionSupported = true
        // Make server respond with one of the sub-protocols sent by our client, in our case: ['Bearer', 'eyToken***'].
        // When the server does not respond with one of both options, the websocket client won't accept the connection.
        // The protocol name "Bearer" is made up, but injecting the auth token via protocol is a common workaround
        // for the issue that the Javascript Websocket class does not allow to add an `Authorization` header [RFR-165].
        options.webSocketSubProtocols = listOf("Bearer")
        vertx.createHttpServer(options)
            .requestHandler(router)
            .listen(port) { serverStartup: AsyncResult<io.vertx.core.http.HttpServer> ->
                completeStartup(
                    serverStartup,
                    startPromise
                )
            }
    }

    /**
     * Finishes the `CollectorApiVerticle` startup process and informs all interested parties about whether
     * it has been successful or not.
     *
     * @param serverStartup The result of the server startup as provided by `Vertx`
     * @param promise A promise to call to inform all waiting parties about success or failure of the startup process
     */
    private fun completeStartup(
        serverStartup: AsyncResult<io.vertx.core.http.HttpServer>,
        promise: Promise<Void>
    ) {
        if (serverStartup.succeeded()) {
            promise.complete()
            LOGGER.info("Successfully started API!")
        } else {
            promise.fail(serverStartup.cause())
            LOGGER.info("Starting API failed!")
        }
    }

    companion object {
        /**
         * The `Logger` used for objects of this class. Configure it by changing the settings in
         * `src/main/resources/vertx-default-jul-logging.properties`.
         */
        private val LOGGER = LoggerFactory.getLogger(HttpServer::class.java)
    }
}
