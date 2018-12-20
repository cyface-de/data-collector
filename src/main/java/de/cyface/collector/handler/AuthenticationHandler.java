/*
 * Copyright 2018 Cyface GmbH
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

import de.cyface.collector.verticle.CollectorApiVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles authentication request on the Cyface Collector API. This is implemented with JSON Web Token (JWT). To get a
 * valid token, the user is required to authenticate using username and password, which issues a new token. This token
 * can then be used to transmit the actual request.
 * 
 * @author Klemens Muthmann
 * @version 1.0.2
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
    private final MongoAuth authProvider;
    /**
     * Authenticator that checks for valid authentications against Java Web Tokens.
     */
    private final JWTAuth jwtAuthProvider;

    /**
     * Creates a new completely initialized <code>AuthenticationHandler</code>.
     * 
     * @param authProvider Authenticator that uses the Mongo user database to store and retrieve credentials.
     * @param jwtAuthProvider Authenticator that checks for valid authentications against Java Web Tokens.
     */
    public AuthenticationHandler(final MongoAuth authProvider, JWTAuth jwtAuthProvider) {
        Validate.notNull(authProvider);
        Validate.notNull(jwtAuthProvider);

        this.authProvider = authProvider;
        this.jwtAuthProvider = jwtAuthProvider;
    }

    @Override
    public void handle(final RoutingContext ctx) {
        try {
            final JsonObject body = ctx.getBodyAsJson();
            LOGGER.info("Receiving authentication request: " + body);
            authProvider.authenticate(body, r -> {
                if (r.succeeded()) {
                    LOGGER.info("Authentication successful!");
                    LOGGER.info(body);
                    final JWTOptions jwtOptions = new JWTOptions().setExpiresInSeconds(60);
                    final String generatedToken = jwtAuthProvider.generateToken(body,
                            jwtOptions.setAlgorithm(CollectorApiVerticle.JWT_HASH_ALGORITHM));
                    LOGGER.info("New JWT Token: " + generatedToken);
                    // Returning the token as response body because the RequestTest fails to read the header
                    // Returning the token as response header because Android fails to read the response body
                    ctx.response().putHeader("Authorization", generatedToken).setStatusCode(200).end(generatedToken);
                } else {
                    ctx.fail(401);
                }
            });
        } catch (DecodeException e) {
            LOGGER.error("Unable to decode body!", e);
            ctx.fail(401);
        }
    }

}
