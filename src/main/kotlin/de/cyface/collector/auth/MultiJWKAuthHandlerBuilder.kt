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

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.AuthenticationHandler

/**
 * An [AuthHandlerBuilder] that verifies JWT tokens against a list of JWKs.
 *
 * A token is accepted if it validates against any one of the provided keys. This supports
 * scenarios where multiple identity providers are in use, including cases where providers
 * share the same `kid` value.
 *
 * @author Klemens Muthmann
 * @property vertx The Vertx instance used to create the authentication providers.
 * @property jwkList The JWK objects to verify tokens against.
 */
class MultiJWKAuthHandlerBuilder(
    private val vertx: Vertx,
    private val jwkList: List<JsonObject>,
) : AuthHandlerBuilder {

    override suspend fun create(apiRouter: Router): AuthenticationHandler {
        val providers = jwkList.map { jwk ->
            JWTAuth.create(vertx, JWTAuthOptions().apply { jwks = listOf(jwk) })
        }
        return MultiProviderJWTAuthHandler(providers)
    }
}