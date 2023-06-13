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
import io.vertx.ext.web.handler.OAuth2AuthHandler

/**
 * Interface for the builder which creates an OAuth2 handler to allow mocking.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
interface OAuth2HandlerBuilder {

    /**
     * Start the creation process of a [OAuth2HandlerBuilder] and provide a [Future], that will be notified about
     * successful or failed completion.
     */
    fun create(): Future<OAuth2AuthHandler>
}
