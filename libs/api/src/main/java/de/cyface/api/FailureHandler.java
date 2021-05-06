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
 * Default failure handler for all failures not directly handled by any of the other handlers.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FailureHandler implements Handler<RoutingContext> {

    /**
     * The logger used for objects of this class. Configure it using <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureHandler.class);

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.error("You seem to have reached an invalid resource: " + ctx.normalisedPath());
        LOGGER.error(ctx.failure());
        if (ctx.response().ended() || ctx.response().closed()) {
            LOGGER.error("Failure in closed response. Unable to inform client! ", ctx.failure());
        } else {
            ctx.response().setStatusCode(404).end("You seem to have reached an invalid resource.");
        }
    }

}
