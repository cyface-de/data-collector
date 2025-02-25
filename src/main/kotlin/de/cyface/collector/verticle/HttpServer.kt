/*
 * Copyright 2020-2025 Cyface GmbH
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

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.coAwait
import org.slf4j.LoggerFactory

/**
 * A wrapper which starts the `HttpServer` for [CollectorApiVerticle].
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @property port The port on which the HTTP server should listen
 */
class HttpServer(
    private val port: Int
) {
    /**
     * The `Logger` used for objects of this class. Configure it by changing the settings in
     * `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(HttpServer::class.java)

    /**
     * Starts the HTTP server provided by this application. This server runs the Cyface Collector REST-API.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @param router The router for all the endpoints the HTTP server should serve
     */
    suspend fun start(vertx: Vertx, router: Router) {
        try {
            val options = HttpServerOptions()
            options.isCompressionSupported = true
            // Make server respond with one of the sub-protocols sent by our client, in our case:
            // ['Bearer', 'eyToken***'].
            // When the server does not respond with one of both options, the websocket client won't accept the
            // connection.
            // The protocol name "Bearer" is made up, but injecting the auth token via protocol is a common workaround
            // for the issue that the Javascript Websocket class does not allow to add an `Authorization`
            // header [RFR-165].
            options.webSocketSubProtocols = listOf("Bearer")
            vertx.createHttpServer(options)
                .requestHandler(router)
                .listen(port).coAwait()
            logger.info("Successfully started API on Port $port!")
        } catch (e: Throwable) {
            logger.error("Failed to start API on Port $port!", e)
            throw e
        }
    }
}
