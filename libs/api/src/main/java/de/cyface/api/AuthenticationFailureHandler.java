/*
 * Copyright (C) 2018 - 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        final boolean closed = context.response().closed();
        final boolean ended = context.response().ended();
        if (!closed && !ended) {
            LOGGER.error("Failing authentication request with 401 since response was closed: {}, ended: {}", closed,
                    ended);
            context.response().setStatusCode(401).end();
        }
    }
}
