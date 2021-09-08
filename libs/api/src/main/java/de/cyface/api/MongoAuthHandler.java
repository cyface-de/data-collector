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
package de.cyface.api;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.web.RoutingContext;

/**
 * Abstract base class for all requests to a collector endpoint. This class makes sure that such requests are properly
 * authorized.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 */
public final class MongoAuthHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAuthHandler.class);
    /**
     * Http code which indicates that the upload request syntax was incorrect.
     */
    public static final int ENTITY_UNPARSABLE = 422;
    /**
     * Http code which indicates that the request is not authorized.
     */
    public static final int UNAUTHORIZED = 403;
    /**
     * An auth provider used by this server to authenticate against the Mongo user database.
     */
    private final MongoAuthentication authProvider;
    /**
     * {@code True} if the {@code #request} needs to be paused when waiting for an async result.
     * <p>
     * This is necessary when the request body is parsed after this handler or else `403: request body already read` if
     * thrown [DAT-749].
     */
    private final boolean pauseAndResume;

    /**
     * Creates a new completely initialized <code>ExporterRequestHandler</code> with access to all required
     * authentication information to authorize a request and fetch the correct data.
     *
     * @param authProvider An auth provider used by this server to authenticate against the Mongo user database.
     * @param pauseAndResume {@code True} if the {@code #request} needs to be paused when waiting for an async result.
     *            This is necessary when the request body is parsed after this handler or else `403: request body
     *            already read` if thrown [DAT-749].
     */
    public MongoAuthHandler(final MongoAuthentication authProvider, final boolean pauseAndResume) {
        Validate.notNull(authProvider);
        this.authProvider = authProvider;
        this.pauseAndResume = pauseAndResume;
    }

    @Override
    public void handle(RoutingContext ctx) {
        LOGGER.info("Received new api request.");
        final var request = ctx.request();
        LOGGER.debug("Request headers: {}", request.headers());

        try {
            // Check authorization before requesting data to reduce DDoS risk
            final var user = ctx.user();
            final var username = user.principal().getString("username");

            // No need to `pause()` the request if the `BodyHandler` is installed first [DAT-749]
            if (pauseAndResume) {
                request.pause();
            }
            authProvider.authenticate(user.principal(), r -> {
                if (pauseAndResume) {
                    request.resume();
                }

                if (!(r.succeeded())) {
                    LOGGER.error("Authorization failed for user {}!", username);
                    ctx.fail(UNAUTHORIZED);
                    return;
                }

                LOGGER.trace("Request authorized for user {}", username);
                ctx.next();
            });
        } catch (final NumberFormatException e) {
            LOGGER.error("Data was not parsable!");
            ctx.fail(ENTITY_UNPARSABLE);
        }
    }
}
