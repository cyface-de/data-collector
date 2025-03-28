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

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.AuthenticationHandler

/**
 * Interface for the builder which creates an [AuthenticationHandler] used to read authentication information from the
 * request.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 */
interface AuthHandlerBuilder {

    /**
     * Create aa [AuthHandlerBuilder].
     *
     * This sometimes requires the API router, for which authentication is going to be created to enable callbacks.
     */
    suspend fun create(apiRouter: Router): AuthenticationHandler
}
