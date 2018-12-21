/**
 * 
 */
package de.cyface.collector.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Handlers failures occuring during authentication and makes sure 401 is returned as HTTP status code on failed
 * authentication attempts.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class AuthenticationFailureHandler implements Handler<RoutingContext> {

    @Override
    public void handle(final RoutingContext context) {
        final boolean closed = context.response().closed();
        final boolean ended = context.response().ended();
        if (!closed && !ended) {
            context.response().setStatusCode(401).end();
        }

    }

}
