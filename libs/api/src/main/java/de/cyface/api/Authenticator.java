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

import java.util.Collections;
import java.util.Objects;

import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import org.apache.commons.lang3.Validate;

import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles authentication request on the Cyface Collector API. This is implemented with JSON Web Token (JWT). To get a
 * valid token, the user is required to authenticate using username and password against the provided endpoint, which
 * issues a new token. This token can then be used to transmit the actual request.
 * <p>
 * To create a new object of this class call the static factory method
 * {@link #setupAuthentication(String, Router, ServerConfig)}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 2.0.0
 */
public final class Authenticator implements Handler<RoutingContext> {

    /**
     * The logger used by objects of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Authenticator.class);
    /**
     * The hashing algorithm used for public and private keys to generate and check JWT tokens.
     */
    public static final String JWT_HASH_ALGORITHM = "HS256";
    /**
     * The number of bytes in one kilobyte. This is used to limit the amount of bytes accepted by an authentication
     * request.
     */
    public static final long BYTES_IN_ONE_KILOBYTE = 1024L;
    /**
     * The number of bytes in one gigabyte. This can be used to limit the amount of data accepted by the server.
     */
    private static final long BYTES_IN_ONE_GIGABYTE = 1_073_741_824L;
    /**
     * Authenticator that uses the Mongo user database to store and retrieve credentials.
     */
    private final transient MongoAuthentication authProvider;
    /**
     * Authenticator that checks for valid authentications against Java Web Tokens.
     */
    private transient final JWTAuth jwtAuthProvider;
    /**
     * The institution which issued the generated JWT token. Usually something like the name of this
     * server.
     */
    private transient final String issuer;
    /**
     * The entity allowed to process requests authenticated with the generated JWT token. This might be
     * a certain server installation or a certain part of an application.
     */
    private transient final String audience;
    /**
     * The number of seconds the JWT authentication token is valid after login.
     */
    private transient final int tokenValidationTime;
    /**
     * The router handling requests that are authenticated by this <code>Authenticator</code>
     */
    private transient final Router authenticatedRouter;

    /**
     * Creates a new completely initialized <code>Authenticator</code>. You may add handlers to be authenticated via
     * {@link #addAuthenticatedHandler(String, Handler, ErrorHandler)}. The rest of the new instances state is immutable.
     *
     * @param authenticatedRouter The router handling requests that are authenticated by this <code>Authenticator</code>
     * @param authProvider Authenticator that uses the Mongo user database to store and retrieve credentials.
     * @param jwtAuthProvider Authenticator that checks for valid authentications against Java Web Tokens
     * @param issuer The institution which issued the generated JWT token. Usually something like the name of this
     *            server
     * @param audience The entity allowed to process requests authenticated with the generated JWT token. This might be
     *            a certain server installation or a certain part of an application
     * @param tokenValidationTime The number of seconds the JWT authentication token is valid after login.
     */
    private Authenticator(final Router authenticatedRouter, final MongoAuthentication authProvider,
                          final JWTAuth jwtAuthProvider, final String issuer, final String audience,
                          final int tokenValidationTime) {
        Validate.notNull(authenticatedRouter);
        Objects.requireNonNull(authProvider, "Parameter authProvider may not be null!");
        Objects.requireNonNull(jwtAuthProvider, "Parameter jwtAuthProvider may not be null!");
        Validate.notEmpty(issuer, "Parameter issuer must not be empty or null!");
        Validate.notEmpty(audience, "Parameter audience must not be empty or null!");
        Validate.isTrue(tokenValidationTime > 0, "Parameter tokenValidationTime must be greater than 0!");

        this.authenticatedRouter = authenticatedRouter;
        this.authProvider = authProvider;
        this.jwtAuthProvider = jwtAuthProvider;
        this.issuer = issuer;
        this.audience = audience;
        this.tokenValidationTime = tokenValidationTime;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        try {
            final var body = ctx.getBodyAsJson();
            LOGGER.debug("Receiving authentication request for user {}", body.getString("username"));
            authProvider.authenticate(body, r -> {
                if (r.succeeded()) {
                    LOGGER.debug("Authentication successful for user {}", body.getString("username"));

                    final var jwtOptions = new JWTOptions().setExpiresInSeconds(tokenValidationTime);
                    jwtOptions.setIssuer(issuer);
                    jwtOptions.setAudience(Collections.singletonList(audience));

                    final var jwtBody = body.put("aud", audience).put("iss", issuer);
                    final var generatedToken = jwtAuthProvider.generateToken(jwtBody,
                            jwtOptions.setAlgorithm(JWT_HASH_ALGORITHM));
                    LOGGER.trace("New JWT Token: {}", generatedToken);
                    ctx.response().putHeader("Authorization", generatedToken).setStatusCode(200).end();
                } else {
                    LOGGER.error("Unsuccessful authentication request for user {}", body.getString("username"));
                    ctx.fail(401);
                }
            });
        } catch (DecodeException e) {
            LOGGER.error("Unable to decode authentication request!");
            ctx.fail(401);
        }
    }

    /**
     * Setups up the login route.
     *
     * @param loginEndpoint The endpoint to be used for login. This endpoint is added to the current path of the
     *            provided <code>router</code>
     * @param router The router to setup authentication on
     * @param serverConfig The HTTP server configuration parameters required
     * @return the authenticator created
     */
    public static Authenticator setupAuthentication(final String loginEndpoint, final Router router,
            final ServerConfig serverConfig) {
        final var authenticator = new Authenticator(router, serverConfig.getAuthProvider(),
                serverConfig.getJwtAuthProvider(), serverConfig.getIssuer(), serverConfig.getAudience(),
                serverConfig.getTokenExpirationTime());
        router.route(loginEndpoint)
                .consumes("application/json")
                .handler(BodyHandler.create().setBodyLimit(2 * BYTES_IN_ONE_KILOBYTE))
                .handler(LoggerHandler.create())
                .handler(authenticator)
                .failureHandler(new AuthenticationFailureHandler());
        return authenticator;
    }

    /**
     * Adds a handler for an endpoint and makes sure that handler is wrapped in the correct authentication handlers.
     *
     * @param endpoint The URL endpoint to wrap
     * @param handler The handler to add with authentication to the <code>Router</code>
     * @param failureHandler The handler to add to handle failures.
     * @return the handler
     */
    public Authenticator addAuthenticatedHandler(final String endpoint, final Handler<RoutingContext> handler, ErrorHandler failureHandler) {
        final var authHandler = JWTAuthHandler.create(jwtAuthProvider);
        authenticatedRouter.post(endpoint)
                .consumes("multipart/form-data")
                .handler(BodyHandler.create().setBodyLimit(BYTES_IN_ONE_GIGABYTE).setDeleteUploadedFilesOnEnd(true))
                .handler(LoggerHandler.create())
                .handler(authHandler)
                .handler(handler)
                .failureHandler(failureHandler);
        return this;
    }
}
