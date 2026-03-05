/*
 * Copyright 2026 Cyface GmbH
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

import de.cyface.collector.handler.HTTPStatus
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthenticationHandler

/**
 * An [AuthenticationHandler] that tries each of a list of [JWTAuth] providers in sequence,
 * accepting the request as soon as one provider successfully verifies the token.
 *
 * This is required when multiple identity providers share the same `kid` value, making it
 * impossible to rely on key lookup by `kid` alone.
 *
 * @author Klemens Muthmann
 * @property providers The JWT authentication providers to try, in order.
 */
class MultiProviderJWTAuthHandler(
    private val providers: List<JWTAuth>,
) : AuthenticationHandler {

    private val validTokenStartString = "Bearer "

    override fun handle(ctx: RoutingContext) {
        val token = extractToken(ctx) ?: run {
            ctx.fail(HTTPStatus.UNAUTHORIZED)
            return
        }
        tryNext(ctx, JsonObject().put("token", token), providers.iterator())
    }

    private fun tryNext(ctx: RoutingContext, credentials: JsonObject, remaining: Iterator<JWTAuth>) {
        if (!remaining.hasNext()) {
            ctx.fail(HTTPStatus.UNAUTHORIZED)
            return
        }
        remaining.next()
            .authenticate(credentials)
            .onSuccess { user ->
                ctx.setUser(user)
                ctx.next()
            }
            .onFailure { tryNext(ctx, credentials, remaining) }
    }

    private fun extractToken(ctx: RoutingContext): String? {
        val header = ctx.request().getHeader("Authorization") ?: return null
        return if (header.startsWith(validTokenStartString)) header.substring(validTokenStartString.length) else null
    }
}
