package de.cyface.collector.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Default failure handler for all failures not directly handled by any of the other handlers.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class FailureHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext ctx) {
        ctx.response().setStatusCode(404).end("You seem to have reached an invalid resource.");
    }

}
