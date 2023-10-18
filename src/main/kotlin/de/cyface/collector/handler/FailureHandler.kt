/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.collector.handler

import io.vertx.core.Vertx
import io.vertx.core.impl.logging.LoggerFactory
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.common.WebEnvironment
import io.vertx.ext.web.handler.impl.ErrorHandlerImpl

/**
 * This custom `ErrorHandlerImpl` extension prints the stacktrace of exceptions from `ctx.fail(e)` in the console.
 *
 * We cannot use `ErrorHandler.create(vertx, true)` as this prints stacktrace in the http response not the console.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.10.1
 * @param vertx The context required to read the `errorTemplateName` from the file system.
 */
class FailureHandler(
    vertx: Vertx?
) : ErrorHandlerImpl(vertx, DEFAULT_ERROR_HANDLER_TEMPLATE, WebEnvironment.development()) {
    override fun handle(ctx: RoutingContext) {
        LOGGER.error("Invalid resource ${ctx.request().absoluteURI()} requested!")
        if (LOGGER.isDebugEnabled) {
            LOGGER.debug("Headers:")
            for ((key, value) in ctx.request().headers()) {
                LOGGER.debug("$key: $value")
            }
        }

        // Printing stacktrace to console is necessary to be able to debug exceptions which only occurs in production
        if (ctx.failure() != null) {
            val request = ctx.request()
            LOGGER.error(
                "Stacktrace: ${request.method()} ${request.absoluteURI()} ${ctx.statusCode()}",
                ctx.failure()
            )
        }

        if (ctx.response().ended() || ctx.response().closed()) {
            LOGGER.error("Failure in closed response. Unable to inform client! ", ctx.failure())
        } else if (ctx.statusCode() == -1) {
            ctx.response().setStatusCode(HTTPStatus.NOT_FOUND).end("You seem to have reached an invalid resource.")
        }

        // Respond to client with pretty error page
        super.handle(ctx)
    }

    companion object {
        /**
         * The logger used for objects of this class. Configure it using `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(FailureHandler::class.java)
    }
}
