/*
 * Copyright 2018-2021 Cyface GmbH
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
package de.cyface.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Handlers failures occurring during authentication and makes sure 401 is returned as HTTP status code on failed
 * authentication attempts.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
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
        LOGGER.error("Reason", context.failure());

        final var response = context.response();
        final boolean closed = response.closed();
        final boolean ended = response.ended();
        final var errorCode = context.statusCode();
        if (!closed && !ended) {
            LOGGER.error("Failing authentication request with {} since response was closed: {}, ended: {}", errorCode,
                    closed, ended);
            context.response().setStatusCode(errorCode).end();
        }
    }
}
