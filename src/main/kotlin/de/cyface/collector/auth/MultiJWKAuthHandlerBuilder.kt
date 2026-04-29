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
import io.vertx.ext.web.handler.JWTAuthHandler

/**
 * An [AuthHandlerBuilder] that verifies JWT tokens against a list of JWKs.
 *
 * All JWKs are loaded into a single [JWTAuth] instance. Vert.x natively selects the correct
 * key by matching the `kid` field in the JWT header against the `kid` of each loaded JWK.
 * Each JWK in the list must therefore carry a unique `kid`.
 *
 * @author Klemens Muthmann
 * @property vertx The Vertx instance used to create the authentication provider.
 * @property jwkList The JWK objects to verify tokens against. Each entry must have a unique `kid`.
 */
class MultiJWKAuthHandlerBuilder(
    private val vertx: Vertx,
    private val jwkList: List<JsonObject>,
) : AuthHandlerBuilder {

    override suspend fun create(apiRouter: Router): AuthenticationHandler {
        val jwtAuth = JWTAuth.create(vertx, JWTAuthOptions().apply { jwks = jwkList })
        return JWTAuthHandler.create(jwtAuth)
    }
}
