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
package de.cyface.collector.handler;

import java.util.Collections;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles authentication request on the Cyface Collector API. This is implemented with JSON Web Token (JWT). To get a
 * valid token, the user is required to authenticate using username and password against the /login endpoint, which
 * issues a new token. This token can then be used to transmit the actual request.
 *
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
public final class AuthenticationHandler implements Handler<RoutingContext> {

    /**
     * The logger used by objects of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationHandler.class);

    /**
     * Authenticator that uses the Mongo user database to store and retrieve credentials.
     */
    private final transient MongoAuthentication authProvider;
    /**
     * Authenticator that checks for valid authentications against Java Web Tokens.
     */
    private final transient JWTAuth jwtAuthProvider;
    /**
     * The institution which issued the generated JWT token. Usually something like the name of this
     * server.
     */
    private final transient String issuer;
    /**
     * The entity allowed to process requests authenticated with the generated JWT token. This might be
     * a certain server installation or a certain part of an application.
     */
    private final transient String audience;
    /**
     * The time in seconds a generated token is valid, before a new token must be acquired.
     */
    private final transient int tokenExpirationTime;

    /**
     * Creates a new completely initialized <code>AuthenticationHandler</code>.
     *
     * @param authProvider Authenticator that uses the Mongo user database to store and retrieve credentials.
     * @param jwtAuthProvider Authenticator that checks for valid authentications against Java Web Tokens.
     * @param issuer The institution which issued the generated JWT token. Usually something like the name of this
     *            server.
     * @param audience The entity allowed to process requests authenticated with the generated JWT token. This might be
     *            a certain server installation or a certain part of an application.
     * @param tokenExpirationTime The time in seconds a generated token is valid, before a new token must be acquired
     */
    public AuthenticationHandler(final MongoAuthentication authProvider, final JWTAuth jwtAuthProvider,
            final String issuer, final String audience, final int tokenExpirationTime) {
        Objects.requireNonNull(authProvider, "Parameter authProvider may not be null!");
        Objects.requireNonNull(jwtAuthProvider, "Parameter jwtAuthProvider may not be null!");
        Validate.notEmpty(issuer, "Parameter issuer must not be empty or null!");
        Validate.notEmpty(audience, "Parameter audience must not be empty or null!");
        Validate.isTrue(tokenExpirationTime > 0, "Parameter tokenExpirationTime must be greater than 0!");

        this.authProvider = authProvider;
        this.jwtAuthProvider = jwtAuthProvider;
        this.issuer = issuer;
        this.audience = audience;
        this.tokenExpirationTime = tokenExpirationTime;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        try {
            final var body = ctx.getBodyAsJson();
            LOGGER.debug("Receiving authentication request for user {}", body.getString("username"));
            authProvider.authenticate(body, r -> {
                if (r.succeeded()) {
                    LOGGER.debug("Authentication successful for user {}", body.getString("username"));

                    final var jwtOptions = new JWTOptions().setExpiresInSeconds(tokenExpirationTime);
                    jwtOptions.setIssuer(issuer);
                    jwtOptions.setAudience(Collections.singletonList(audience));

                    final var jwtBody = body.put("aud", audience).put("iss", issuer);
                    final var generatedToken = jwtAuthProvider.generateToken(jwtBody,
                            jwtOptions.setAlgorithm(CollectorApiVerticle.JWT_HASH_ALGORITHM));
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
}