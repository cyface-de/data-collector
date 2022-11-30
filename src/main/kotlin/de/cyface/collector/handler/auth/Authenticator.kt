/*
 * Copyright 2018-2022 Cyface GmbH
 *
 * This file is part of the Cyface API Library.
 *
 * The Cyface API Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface API Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface API Library. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.handler.auth

import de.cyface.api.AuthenticationFailureHandler
import de.cyface.collector.handler.HTTPStatus
import de.cyface.collector.verticle.Config
import io.vertx.core.Handler
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials
import io.vertx.ext.auth.impl.jose.JWS.RS256
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.mongo.MongoAuthentication
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.LoggerHandler
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.Locale

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
 * @version 4.0.3
 * @since 1.0.0
 * @property authProvider Authenticator that uses the Mongo user database to store and retrieve credentials.
 * @property jwtAuthProvider Authenticator that checks for valid authentications against Java Web Tokens
 * @property issuer The institution which issued the generated JWT token. Usually something like the name of this
 *            server
 * @property audience The entity allowed processing requests authenticated with the generated JWT token. This might be
 *            a certain server installation or a certain part of an application
 * @property tokenValidationTime The number of seconds the JWT authentication token is valid after login.
 */
class Authenticator private constructor(
    @Transient val authProvider: MongoAuthentication,
    @Transient val jwtAuthProvider: JWTAuth,
    @Transient val issuer: String,
    @Transient val audience: String,
    @Transient val tokenValidationTime: Int
) : Handler<RoutingContext> {
    init {
        Validate.notEmpty(issuer, "Parameter issuer must not be empty or null!")
        Validate.notEmpty(audience, "Parameter audience must not be empty or null!")
        Validate.isTrue(tokenValidationTime > 0, "Parameter tokenValidationTime must be greater than 0!")
    }

    override fun handle(ctx: RoutingContext) {
        try {
            val body = ctx.body().asJsonObject()
            // Ensure username, e.g. email-address, is not case-sensitive
            val caseSensitiveUsername = body.getString("username")
            val username = caseSensitiveUsername.lowercase(Locale.GERMANY)
            val password = body.getString("password")
            val credentials = UsernamePasswordCredentials(username, password)
            LOGGER.debug("Receiving authentication request for user {}", username)
            val authentication = authProvider.authenticate(credentials)
            authentication.onSuccess { user ->
                try {
                    val principal = user.principal()
                    if (activated(principal)) {
                        LOGGER.debug("Authentication successful for user {}", username)

                        val jwtOptions = JWTOptions()
                            .setAlgorithm(JWT_HASH_ALGORITHM)
                            .setExpiresInSeconds(tokenValidationTime)
                            .setIssuer(issuer)
                            .setAudience(Collections.singletonList(audience))
                        val jwtBody = credentials.toJson().put("aud", audience).put("iss", issuer)
                        val generatedToken = jwtAuthProvider.generateToken(jwtBody, jwtOptions)
                        LOGGER.trace("New JWT Token: {}", generatedToken)

                        ctx.response().putHeader("Authorization", generatedToken).setStatusCode(HTTPStatus.OK).end()
                    } else {
                        LOGGER.error("Authentication failed, user not activated: {}", username)
                        ctx.fail(HTTPStatus.PRECONDITION_REQUIRED)
                    }
                } catch (e: RuntimeException) {
                    ctx.fail(e)
                }
            }
            authentication.onFailure { e ->
                LOGGER.error("Unsuccessful authentication request for user {}", username)
                ctx.fail(HTTPStatus.UNAUTHORIZED, e)
            }
        } catch (e: DecodeException) {
            LOGGER.error("Unable to decode authentication request!")
            ctx.fail(HTTPStatus.UNAUTHORIZED, e)
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
    fun activated(principal: JsonObject): Boolean {
        return !principal.containsKey("activated") || principal.getBoolean("activated")
    }

    companion object {
        /**
         * The logger used by objects of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
         */
        private val LOGGER = LoggerFactory.getLogger(Authenticator::class.java)

        /**
         * The hashing algorithm used for public and private keys to generate and check JWT tokens.
         */
        val JWT_HASH_ALGORITHM = RS256

        /**
         * The number of bytes in one kilobyte. This is used to limit the amount of bytes accepted by an authentication
         * request.
         */
        const val BYTES_IN_ONE_KILOBYTE = 1024L

        /**
         * Setups up the login route.
         *
         * @param loginEndpoint The endpoint to be used for login. This endpoint is added to the current path of the
         *            provided <code>router</code>
         * @param router The router to set up authentication on
         * @param config The HTTP server configuration parameters required
         */
        fun setupAuthentication(
            loginEndpoint: String,
            router: Router,
            config: Config
        ) {
            val authenticator = Authenticator(
                config.authProvider,
                config.jwtAuthProvider,
                config.issuer,
                config.audience,
                config.tokenExpirationTime
            )
            router.route(loginEndpoint)
                .consumes("application/json")
                .handler(LoggerHandler.create())
                .handler(BodyHandler.create().setBodyLimit(2 * BYTES_IN_ONE_KILOBYTE))
                .handler(authenticator)
                .failureHandler(AuthenticationFailureHandler())
        }
    }
}
