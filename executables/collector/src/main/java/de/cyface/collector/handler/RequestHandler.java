/*
 * Copyright 2019-2021 Cyface GmbH
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

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.RoutingContext;

/**
 * Abstract base class for all requests to a collector endpoint. This class makes sure that such requests are properly
 * authorized.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 5.3.0
 */
abstract class RequestHandler implements Handler<RoutingContext> {

    private final MongoAuth authProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    /**
     * Creates a new completely initialized <code>ExporterRequestHandler</code> with access to all required
     * authentication information to authorize a request and fetch the correct data.
     *
     * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
     */
    public RequestHandler(final MongoAuth authProvider) {
        Validate.notNull(authProvider);
        this.authProvider = authProvider;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.info("Received new data export request.");
        final var request = ctx.request();
        LOGGER.debug("Request headers: {}", request.headers());

        try {
            // Check authorization before requesting data to reduce DDoS risk
            final var user = ctx.user();

            authProvider.authenticate(user.principal(), r -> {
                if (!(r.succeeded())) {
                    LOGGER.error("Authorization failed for user {}!",
                            user.principal().getString(authProvider.getUsernameField()));
                    ctx.fail(403);
                    return;
                }

                LOGGER.trace("Request authorized for user {}", authProvider.getUsernameField());
                handleAuthorizedRequest(ctx);
            });
        } catch (final NumberFormatException e) {
            LOGGER.error("Data was not parsable!");
            ctx.fail(422);
        }
    }

    /**
     * This is the method that should be implemented by subclasses to carry out the business logic on an authorized
     * request.
     * 
     * @param ctx Vert.x request context
     */
    protected abstract void handleAuthorizedRequest(final RoutingContext ctx);
}
