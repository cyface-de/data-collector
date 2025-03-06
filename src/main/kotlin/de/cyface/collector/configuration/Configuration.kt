/*
 * Copyright 2022-2025 Cyface GmbH
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
package de.cyface.collector.configuration

import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get

@Suppress("ForbiddenComment")
// TODO: Remove the HTTP Path Parameter by switching to returning a relative Location header.
//  This requires adapted clients.
/**
 * A POJO representing the configuration provided to this application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @property httpHost The host name of the server serving this collector service.
 * @property httpPort The Port providing this collector service.
 * @property httpPath The path under which this service is deployed.
 * This is required to send the client proper absolute locations for
 * @property mongoDb The Mongo database configuration as described in the Vert.x documentation.
 * @property uploadExpiration The time an upload session stays valid to be resumed in the future, in milliseconds.
 * @property measurementPayloadLimit The maximum size in bytes accepted for a single measurement.
 * @property metricsEnabled `true` if prometheus metrics should be collected; `false` otherwise.
 * @property storageTypeJson The type of storage to use for storing the binary data blobs.
 * @property authConfig The configuration for the authentication.
 */
data class Configuration(
    val httpHost: String,
    val httpPort: Int,
    val httpPath: String,
    val mongoDb: JsonObject,
    val uploadExpiration: Long,
    val measurementPayloadLimit: Long,
    val metricsEnabled: Boolean,
    val storageTypeJson: JsonObject,
    val authConfig: JsonObject
) {
    companion object {
        /**
         * Deserialize a Vert.x JSON configuration into a `Configuration` instance.
         */
        fun deserialize(json: JsonObject): Configuration {
            try {
                val httpHost = requireNotNull(json.get<String?>("http.host")) {
                    "The http.host parameter is missing. Refer to the documentation or an example file."
                }
                val httpPort = requireNotNull(json.get<Int?>("http.port")) {
                    "The http.port parameter is missing. Refer to the documentation or an example file."
                }
                val httpPath = requireNotNull(json.get<String?>("http.endpoint")) {
                    "The http.endpoint parameter is missing. Refer to the documentation or an example file."
                }
                val mongoDb = requireNotNull(json.get<JsonObject?>("mongo.db")) {
                    "The mongo.db parameter is missing. Refer to the documentation or an example file."
                }
                val uploadExpiration = requireNotNull(json.get<Long?>("upload.expiration")) {
                    "The upload.expiration parameter is missing. Refer to the documentation or an example file."
                }
                val measurementPayloadLimit = requireNotNull(json.get<Long?>("measurement.payload.limit")) {
                    "The measurement.payload.limit parameter is missing. Refer to the documentation or an example file."
                }
                val metricsEnabled = requireNotNull(json.get<Boolean?>("metrics.enabled")) {
                    "The metrics.enabled parameter is missing. Refer to the documentation or an example file."
                }
                val storageTypeJson = requireNotNull(json.get<JsonObject?>("storage-type")) {
                    "The storage-type parameter is missing. Refer to the documentation or an example file."
                }
                val authConfig = requireNotNull(json.get<JsonObject?>("auth")) {
                    "The auth parameter is missing. Refer to the documentation or an example file."
                }

                return Configuration(
                    httpHost,
                    httpPort,
                    httpPath,
                    mongoDb,
                    uploadExpiration,
                    measurementPayloadLimit,
                    metricsEnabled,
                    storageTypeJson,
                    authConfig,
                )
            } catch (e: IllegalArgumentException) {
                throw InvalidConfig("Some parameters are missing. Refer to the documentation or an example file.", e)
            }
        }
    }
}
