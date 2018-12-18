/**
 * 
 */
package de.cyface.collector.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class UserCreationHandler implements Handler<RoutingContext> {

    @Override
    public void handle(final RoutingContext event) {
        event.response().setStatusCode(201).end();
    }

}
