/*
 * Copyright 2023-2025 Cyface GmbH
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
package de.cyface.collector.auth

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.impl.UserImpl
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.UUID
import io.vertx.ext.web.handler.AuthenticationHandler

/**
 * Mocked OAuth2 builder which creates an OAuth2 handler for testing.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 */
class MockedHandlerBuilder : AuthHandlerBuilder {

    override suspend fun create(apiRouter: Router): AuthenticationHandler {
        val handler = object : AuthenticationHandler {
            override fun handle(event: RoutingContext) {
                val principal = JsonObject()
                    .put("username", "test-user")
                    .put("sub", UUID.randomUUID()) // user id
                val user = UserImpl(principal, JsonObject())
                event.setUser(user)

                // From AuthenticationHandlerImpl.handle @ `authenticate(ctx, authN -> {..})`
                // event.session()?.regenerateId() - this leads to SessionExpired exception, thus, commented out
                // proceed with the router
                if (!event.request().isEnded) {
                    event.request().resume()
                }
                postAuthentication(event)
            }

            // From AuthenticationHandlerInternal
            private fun postAuthentication(event: RoutingContext) {
                event.next()
            }
        }
        return handler
    }
}
