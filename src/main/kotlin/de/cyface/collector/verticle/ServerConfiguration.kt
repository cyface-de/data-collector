/*
 * Copyright 2024-2025 Cyface GmbH
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
package de.cyface.collector.verticle

/**
 * A parameter wrapper object to initialize a [CollectorApiVerticle].
 *
 * @author Klemens Muthmann
 * @property port The HTTP Server port this service is accessible from.
 * @property httpEndpoint The endpoint this service is accessible at. This is required to set the Location header in
 * PreRequests correctly.
 * @property measurementPayloadLimit The maximum size of measurement sensor data.
 * @property uploadExpirationTimeInMillis The time a request might stay open.
 */
data class ServerConfiguration(
    val port: Int,
    val httpEndpoint: String,
    val measurementPayloadLimit: Long,
    val uploadExpirationTimeInMillis: Long,
)
