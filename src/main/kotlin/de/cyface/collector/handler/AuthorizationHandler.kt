/*
 * Copyright 2022-2023 Cyface GmbH
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
package de.cyface.collector.handler

import de.cyface.api.PauseAndResumeStrategy
import de.cyface.api.model.User
import de.cyface.collector.handler.HTTPStatus.ENTITY_UNPARSABLE
import io.vertx.core.Handler
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.Oauth2Credentials
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A `RequestHandler` to ensure correct user authentication.
 * This should be one of the first handlers on each route requiring authorization.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @property authProvider An auth provider used by this server to authenticate against an OAuth2 endpoint.
 * @property mongoDatabase The database to check which users a {@code manager} user has access to.
 * @property strategy The pause and resume strategy to be used which wraps async calls
 *                    in {@link #handle(RoutingContext)}.
 */
class AuthorizationHandler(
    private val authProvider: OAuth2Auth,
    private val mongoDatabase: MongoClient,
    private val strategy: PauseAndResumeStrategy
) : Handler<RoutingContext> {
    override fun handle(context: RoutingContext) {
        try {
            LOGGER.info("Received new request.")
            val request: HttpServerRequest = context.request()
            val headers = request.headers()
            LOGGER.debug("Request headers: {}", headers)

            // Check authorization
            // FIXME: double check only authenticated users arrive here
            // TODO: use OAuth2 roles to handle group permissions, but not required in collector

            // Inform next handler which user is authenticated
            val credentials = Oauth2Credentials(context.user().principal())
            val username = credentials.username
            // The "sub" is the subject claim which represents the unique id of the authenticated user.
            val uuid = context.user().principal().getString("sub")
            val user = User(UUID.fromString(uuid), username)
            context.put("logged-in-user", user)

            context.next()
        } catch (e: NumberFormatException) {
            LOGGER.error("Data was not parsable!")
            context.fail(ENTITY_UNPARSABLE, e)
        }
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by adapting the values in
         * <code>src/main/resources/logback.xml</code>.
         */
        private val LOGGER = LoggerFactory.getLogger(AuthorizationHandler::class.java)
    }
}
