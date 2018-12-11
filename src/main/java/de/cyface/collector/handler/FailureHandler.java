package de.cyface.collector.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Default failure handler for all failures not directly handled by any of the other handlers.
 * 
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
public class FailureHandler implements Handler<RoutingContext> {

    /**
     * The logger used for objects of this class. Configure it using <code>src/main/resources/logback.xml</code>.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(FailureHandler.class);

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.error("You seem to have reached an invalid resource.");
        if (ctx.response().ended() || ctx.response().closed()) {
            LOGGER.error("Failure in closed response. Unable to inform client! ", ctx.failure());
        } else {
            ctx.response().setStatusCode(404).end("You seem to have reached an invalid resource.");
        }
    }

}
