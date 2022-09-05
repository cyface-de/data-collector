package de.cyface.collector.handler

import de.cyface.collector.handler.MeasurementHandler.Companion.NOT_FOUND
import de.cyface.collector.handler.exception.UnexpectedContentRange
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

class MeasurementFailureHandler(private val ctx: RoutingContext): Handler<Throwable> {
    override fun handle(event: Throwable) {
        when (event) {
            is UnexpectedContentRange -> {
                // client sends a new pre-request for this upload
                ctx.response().setStatusCode(NOT_FOUND).end()
            }

            else -> {
                LOGGER.debug(event.localizedMessage)
                ctx.fail(500, event)
            }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MeasurementFailureHandler::class.java)
    }

}