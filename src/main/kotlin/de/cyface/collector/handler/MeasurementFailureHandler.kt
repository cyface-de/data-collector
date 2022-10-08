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

import de.cyface.collector.handler.HTTPStatus.NOT_FOUND
import de.cyface.collector.handler.HTTPStatus.SERVER_ERROR
import de.cyface.collector.handler.exception.UnexpectedContentRange
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * A handler to process exception occuring during the reception of new measurements.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property ctx The failed routing context, containing the error information.
 */
class MeasurementFailureHandler(private val ctx: RoutingContext) : Handler<Throwable> {
    override fun handle(event: Throwable) {
        when (event) {
            is UnexpectedContentRange -> {
                // client sends a new pre-request for this upload
                ctx.response().setStatusCode(NOT_FOUND).end()
            }

            else -> {
                LOGGER.debug(event.localizedMessage)
                ctx.fail(SERVER_ERROR, event)
            }
        }
    }

    companion object {
        /**
         * The logger used by objects of this class. Configure it using `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(MeasurementFailureHandler::class.java)
    }
}
