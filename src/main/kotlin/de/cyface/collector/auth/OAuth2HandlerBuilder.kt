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
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.OAuth2AuthHandler
import java.net.URL

/**
 * Keycloak OAuth2 builder which creates an OAuth2 handler.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 * @property vertx
 * @property apiRouter
 * @property callbackUrl The callback URL you entered in your provider admin console.
 * @property options the oauth configuration.
 */
class OAuth2HandlerBuilder(
    private val vertx: Vertx,
    private val apiRouter: Router,
    private val callbackUrl: URL,
    private val options: OAuth2Options,
) : AuthHandlerBuilder {

    override fun create(): Future<OAuth2AuthHandler> {
        val promise = Promise.promise<OAuth2AuthHandler>()

        KeycloakAuth.discover(vertx, options)
            .onSuccess {
                val callbackAddress = apiRouter.get(callbackUrl.path)
                val oauth2Handler = OAuth2AuthHandler.create(vertx, it, callbackUrl.toURI().toString())
                    .setupCallback(callbackAddress)
                promise.complete(oauth2Handler)
            }
            .onFailure { promise.fail(it) }

        return promise.future()
    }
}
