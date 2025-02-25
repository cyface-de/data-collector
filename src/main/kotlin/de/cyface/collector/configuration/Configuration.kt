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
import org.apache.commons.lang3.Validate

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
                val httpHost = Validate.notEmpty(json.get<String>("http.host"))
                val httpPort = json.get<Int>("http.port")
                val httpPath = json.get<String>("http.endpoint")
                val mongoDb = json.get<JsonObject>("mongo.db")
                val uploadExpiration = json.get<Long>("upload.expiration")
                val measurementPayloadLimit = json.get<Long>("measurement.payload.limit")
                val metricsEnabled = json.get<Boolean>("metrics.enabled")
                val storageTypeJson = json.get<JsonObject>("storage-type")
                val authConfig = json.getJsonObject("auth")

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
            } catch (@Suppress("TooGenericExceptionCaught") e: NullPointerException) {
                throw InvalidConfig("Some parameters are missing. Refer to the documentation or an example file.", e)
            }
        }
    }
}
