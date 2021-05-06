/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.api;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import org.apache.commons.lang3.Validate;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Abstract base class for all requests to an provider endpoint. This class makes sure that such requests are properly
 * authorized.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 1.0.0
 */
public abstract class RequestHandler implements Handler<RoutingContext> {

    private final MongoAuth authProvider;
    private final MongoClient mongoUserDatabase;
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);
    public final static String DEFAULT_CHARSET = "UTF-8";

    /**
     * Creates a new completely initialized <code>ProviderRequestHandler</code> with access to all required
     * authentication information to authorize a request and fetch the correct data.
     *
     * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
     * @param mongoUserDatabase The Mongo user database containing all information about users
     */
    public RequestHandler(final MongoAuth authProvider, final MongoClient mongoUserDatabase) {
        Validate.notNull(authProvider);
        Validate.notNull(mongoUserDatabase);

        this.authProvider = authProvider;
        this.mongoUserDatabase = mongoUserDatabase;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.info("Received new request.");
        final HttpServerRequest request = ctx.request();
        LOGGER.debug("Request headers: {}", request.headers());

        try {
            // Check authorization before requesting data to reduce DDoS risk
            final User user = ctx.user();

            authProvider.authenticate(user.principal(), r -> {
                if (!(r.succeeded())) {
                    LOGGER.error("Authorization failed for user %s!",
                            user.principal().getString(authProvider.getUsernameField()));
                    ctx.fail(403);
                    return;
                }

                handleAuthorizedRequest(ctx, user.principal().getString("username"));
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
     * @param userName The usernames for which the request is authorized to provide data
     */
    protected abstract void handleAuthorizedRequest(final RoutingContext ctx, final String userName);
}
