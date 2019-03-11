/*
 * Copyright 2018 Cyface GmbH
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
package de.cyface.collector.handler;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Handlers failures occuring during authentication and makes sure 401 is returned as HTTP status code on failed
 * authentication attempts.
 * 
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
public final class AuthenticationFailureHandler implements Handler<RoutingContext> {

    /**
     * Logger used by objects of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFailureHandler.class);

    @Override
    public void handle(final RoutingContext context) {
        LOGGER.error("Received failure " + context.failed());
        LOGGER.error(context.failure());

        final boolean closed = context.response().closed();
        final boolean ended = context.response().ended();
        if (!closed && !ended) {
            LOGGER.error("Failing authentication request with 401 since response was closed: " + closed + ", ended: "
                    + ended);
            context.response().setStatusCode(401).end();
        }

    }

}
