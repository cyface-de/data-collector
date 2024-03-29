/*
 * Copyright 2022-2023 Cyface GmbH
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
import java.net.URL
import java.nio.file.Path

/**
 * A POJO representing the configuration provided to this application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @property serviceHttpAddress The [URL] hosting the collector service.
 * @property mongoDb The Mongo database configuration as described in the Vert.x documentation.
 * @property uploadExpiration The time an upload session stays valid to be resumed in the future, in milliseconds.
 * @property measurementPayloadLimit The maximum size in bytes accepted for a single measurement.
 * @property metricsEnabled `true` if prometheus metrics should be collected; `false` otherwise.
 * @property storageType The type of storage to use for storing the binary data blobs.
 * @property oauthCallback The callback URL you entered in your provider admin console.
 * @property oauthClient The name of the oauth client to contact.
 * @property oauthSecret The secret of the oauth client to contact.
 * @property oauthSite The Root URL for the provider without trailing slashes.
 * @property oauthTenant The name of the oauth realm to contact.
 */
data class Configuration(
    val serviceHttpAddress: URL,
    val mongoDb: JsonObject,
    val uploadExpiration: Long,
    val measurementPayloadLimit: Long,
    val metricsEnabled: Boolean,
    val storageType: StorageType,
    val authType: AuthType,
    val oauthCallback: URL,
    val oauthClient: String,
    val oauthSecret: String,
    val oauthSite: URL,
    val oauthTenant: String
) {
    companion object {
        /**
         * Deserialize a Vert.x JSON configuration into a `Configuration` instance.
         */
        fun deserialize(json: JsonObject): Configuration {
            try {
                val httpHost = Validate.notEmpty(json.get<String>("http.host"))
                val httpPort = json.get<Int>("http.port")
                val httpEndpoint = vertxEndpoint(json["http.endpoint"])
                val serviceHttpAddress = URL("https", httpHost, httpPort, httpEndpoint)
                val mongoDb = json.get<JsonObject>("mongo.db")
                val uploadExpiration = json.get<Long>("upload.expiration")
                val measurementPayloadLimit = json.get<Long>("measurement.payload.limit")
                val metricsEnabled = json.get<Boolean>("metrics.enabled")
                val storageTypeJson = json.get<JsonObject>("storage-type")
                val storageType = storageType(storageTypeJson)
                val authType = AuthType.valueOf(json.getString("auth-type").replaceFirstChar(Char::titlecase))
                val oauthCallback = URL(json.get<String>("oauth.callback"))
                val oauthClient = json.get<String>("oauth.client")
                val oauthSecret = json.get<String>("oauth.secret")
                val oauthSite = URL(json.get<String>("oauth.site"))
                val oauthTenant = json.get<String>("oauth.tenant")

                return Configuration(
                    serviceHttpAddress,
                    mongoDb,
                    uploadExpiration,
                    measurementPayloadLimit,
                    metricsEnabled,
                    storageType,
                    authType,
                    oauthCallback,
                    oauthClient,
                    oauthSecret,
                    oauthSite,
                    oauthTenant
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: NullPointerException) {
                throw InvalidConfig("Some parameters are missing. Refer to the documentation or an example file.", e)
            }
        }

        /**
         * Provide the [StorageType] configured for this data collector.
         */
        private fun storageType(storageTypeConfig: JsonObject): StorageType {
            when (val storageTypeString = storageTypeConfig.getString("type")) {
                "gridfs" -> {
                    val uploadFolder = Path.of(storageTypeConfig.getString("uploads-folder", "file-uploads/"))
                    return GridFsStorageType(uploadFolder)
                }

                "google" -> {
                    val collectionName = storageTypeConfig.get<String>("collection-name")
                    val projectIdentifier = storageTypeConfig.get<String>("project-identifier")
                    val bucketName = storageTypeConfig.get<String>("bucket-name")
                    val credentialsFile = storageTypeConfig.get<String>("credentials-file")
                    return GoogleCloudStorageType(
                        collectionName,
                        projectIdentifier,
                        bucketName,
                        credentialsFile
                    )
                }

                null -> throw InvalidConfig(
                    "Storage type configuration missing. " +
                        "Please provide either a Google or GridFS Storage type."
                )

                else -> throw InvalidConfig("Invalid storage type $storageTypeString!")
            }
        }

        /**
         * Provide the HTTP endpoint configured for use with a Vertx router.
         *
         * This means a '*' is appended as the last path element to match all
         * requests.
         *
         * @param endpoint The original endpoint provided by the configuration.
         */
        private fun vertxEndpoint(endpoint: String): String {
            Validate.notEmpty(
                endpoint,
                "Endpoint not found. Please use the parameter $endpoint."
            )
            val builder = StringBuilder(endpoint)
            val lastChar = endpoint.last()
            if (lastChar == '/') {
                builder.append("*")
            } else if (lastChar != '*') {
                builder.append("/*")
            }
            return builder.toString()
        }
    }
}
