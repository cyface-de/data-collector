/*
 * Copyright 2023 Cyface GmbH
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

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.impl.UserImpl
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.OAuth2AuthHandler
import java.util.UUID

/**
 * Mocked OAuth2 builder which creates an OAuth2 handler for testing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
class MockedHandlerBuilder : AuthHandlerBuilder {

    override fun create(): Future<OAuth2AuthHandler> {
        val handler: OAuth2AuthHandler = object : OAuth2AuthHandler {
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

            override fun extraParams(extraParams: JsonObject?): OAuth2AuthHandler {
                return this
            }

            override fun withScope(scope: String?): OAuth2AuthHandler {
                return this
            }

            override fun withScopes(scopes: MutableList<String>?): OAuth2AuthHandler {
                return this
            }

            override fun prompt(prompt: String?): OAuth2AuthHandler {
                return this
            }

            override fun pkceVerifierLength(length: Int): OAuth2AuthHandler {
                return this
            }

            override fun setupCallback(route: Route?): OAuth2AuthHandler {
                return this
            }
        }
        return Future.succeededFuture(handler)
    }
}
