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

import de.cyface.collector.model.User
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A handler to ensure correct user authentication.
 * This should be one of the first handlers on each route requiring authorization.
 *
 * Reads user identity from the JWT access token that was already decoded by the auth handler.
 * Requires the token's `aud` claim to include the backend's configured client ID so that
 * local JWT validation succeeds.
 */
class AuthorizationHandler : Handler<RoutingContext> {
    override fun handle(context: RoutingContext) {
        try {
            LOGGER.info("Received new request.")

            val claims = context.user()?.attributes()?.getJsonObject("accessToken")
                ?: error("Missing decoded access token (JWT audience validation may have failed)")
            val username = claims.getString("preferred_username")
                ?: error("Missing preferred_username in access token")
            val uuid = claims.getString("sub")
                ?: error("Missing sub in access token")
            val user = User(UUID.fromString(uuid), username)
            context.put("logged-in-user", user)

            context.next()
        } catch (e: RuntimeException) {
            context.fail(e)
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
