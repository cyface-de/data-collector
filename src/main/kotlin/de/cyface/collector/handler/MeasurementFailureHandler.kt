package de.cyface.collector.handler

import de.cyface.api.Authorizer.ENTITY_UNPARSABLE
import de.cyface.collector.handler.MeasurementHandler.Companion.NOT_FOUND
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.Unparsable
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

class MeasurementFailureHandler(val ctx: RoutingContext): Handler<Throwable> {
    override fun handle(event: Throwable) {
        when (event) {
            is PayloadTooLarge -> {
                LOGGER.error("Response: 422", event)
                ctx.fail(ENTITY_UNPARSABLE, event)
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