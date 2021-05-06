/*
 * Copyright (C) 2018 - 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.api;

import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Handlers failures occurring during authentication and makes sure 401 is returned as HTTP status code on failed
 * authentication attempts.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.0.0
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
