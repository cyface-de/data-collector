/*
 * Copyright 2025 Cyface GmbH
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
 * A Builder for an AuthenticationHandler, that takes all the necessary information directly from configuration
 * parameters.
 * If your identity provider supports discovery, you should use the [OAuth2HandlerBuilder] instead.
 *
 * @author Klemens Muthmann
 * @property vertx The Vertx instance to use for creating the AuthenticationHandler.
 * @property jwkJson The JSON object containing the JWK information.
 */
class JWKAuthHandlerBuilder(
    private val vertx: Vertx,
    private val jwkJson: JsonObject,
): AuthHandlerBuilder {
    override suspend fun create(apiRouter: Router): AuthenticationHandler {
        // 2. JWTAuthOptions erstellen
        val jwtAuthOptions = JWTAuthOptions()
        jwtAuthOptions.jwks = listOf<JsonObject>(jwkJson)

        // 3. JWTAuth erstellen
        val jwtAuth = JWTAuth.create(vertx, jwtAuthOptions)
        return JWTAuthHandler.create(jwtAuth)
    }

}
