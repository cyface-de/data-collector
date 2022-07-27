/*
 * Copyright 2018-2022 Cyface GmbH
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

import static io.vertx.ext.auth.impl.jose.JWS.RS256;

import java.util.Collections;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;

/**
 * Handles authentication request on the Cyface Vert.X APIs. This is implemented with JSON Web Token (JWT). To get a
 * valid token, the user is required to authenticate using username and password against the provided endpoint, which
 * issues a new token. This token can then be used to transmit the actual request.
 * <p>
 * To create a new object of this class call the static factory method
 * {@link #setupAuthentication(String, Router, AuthenticatedEndpointConfig)}.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 4.0.1
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
    public static final String JWT_HASH_ALGORITHM = RS256;
    /**
     * The number of bytes in one kilobyte. This is used to limit the amount of bytes accepted by an authentication
     * request.
     */
    public static final long BYTES_IN_ONE_KILOBYTE = 1024L;
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
     * The entity allowed processing requests authenticated with the generated JWT token. This might be
     * a certain server installation or a certain part of an application.
     */
    private transient final String audience;
    /**
     * The number of seconds the JWT authentication token is valid after login.
     */
    private transient final int tokenValidationTime;

    /**
     * Creates a new completely initialized <code>Authenticator</code>.
     *
     * @param authProvider Authenticator that uses the Mongo user database to store and retrieve credentials.
     * @param jwtAuthProvider Authenticator that checks for valid authentications against Java Web Tokens
     * @param issuer The institution which issued the generated JWT token. Usually something like the name of this
     *            server
     * @param audience The entity allowed processing requests authenticated with the generated JWT token. This might be
     *            a certain server installation or a certain part of an application
     * @param tokenValidationTime The number of seconds the JWT authentication token is valid after login.
     */
    private Authenticator(final MongoAuthentication authProvider,
            final JWTAuth jwtAuthProvider, final String issuer, final String audience,
            final int tokenValidationTime) {
        Objects.requireNonNull(authProvider, "Parameter authProvider may not be null!");
        Objects.requireNonNull(jwtAuthProvider, "Parameter jwtAuthProvider may not be null!");
        Validate.notEmpty(issuer, "Parameter issuer must not be empty or null!");
        Validate.notEmpty(audience, "Parameter audience must not be empty or null!");
        Validate.isTrue(tokenValidationTime > 0, "Parameter tokenValidationTime must be greater than 0!");

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
            final var authentication = authProvider.authenticate(body);
            authentication.onSuccess(user -> {
                try {
                    final var principal = user.principal();
                    if (activated(principal)) {
                        LOGGER.debug("Authentication successful for user {}", body.getString("username"));

                        final var jwtOptions = new JWTOptions()
                                .setAlgorithm(JWT_HASH_ALGORITHM)
                                .setExpiresInSeconds(tokenValidationTime)
                                .setIssuer(issuer)
                                .setAudience(Collections.singletonList(audience));
                        final var jwtBody = body.put("aud", audience).put("iss", issuer);
                        final var generatedToken = jwtAuthProvider.generateToken(jwtBody, jwtOptions);
                        LOGGER.trace("New JWT Token: {}", generatedToken);

                        ctx.response().putHeader("Authorization", generatedToken).setStatusCode(200).end();
                    } else {
                        LOGGER.error("Authentication failed, user not activated: {}", body.getString("username"));
                        ctx.fail(428);
                    }
                } catch (RuntimeException e) {
                    ctx.fail(e);
                }
            });
            authentication.onFailure(e -> {
                LOGGER.error("Unsuccessful authentication request for user {}", body.getString("username"));
                ctx.fail(401, e);
            });
        } catch (DecodeException e) {
            LOGGER.error("Unable to decode authentication request!");
            ctx.fail(401, e);
        }
    }

    /**
     * Checks if the user account is activated.
     * <p>
     * Only self-registered accounts need to be activated and, thus, contain the "activated" field.
     *
     * @param principal the underlying principal of the user to check
     * @return {@code true} if the user account is activated
     */
    static boolean activated(final JsonObject principal) {
        return !principal.containsKey("activated") || principal.getBoolean("activated");
    }

    /**
     * Setups up the login route.
     *
     * @param loginEndpoint The endpoint to be used for login. This endpoint is added to the current path of the
     *            provided <code>router</code>
     * @param router The router to set up authentication on
     * @param config The HTTP server configuration parameters required
     */
    public static void setupAuthentication(final String loginEndpoint, final Router router,
            final AuthenticatedEndpointConfig config) {
        final var authenticator = new Authenticator(config.getAuthProvider(),
                config.getJwtAuthProvider(), config.getIssuer(), config.getAudience(),
                config.getTokenExpirationTime());
        router.route(loginEndpoint)
                .consumes("application/json")
                .handler(LoggerHandler.create())
                .handler(BodyHandler.create().setBodyLimit(2 * BYTES_IN_ONE_KILOBYTE))
                .handler(authenticator)
                .failureHandler(new AuthenticationFailureHandler());
    }
}
