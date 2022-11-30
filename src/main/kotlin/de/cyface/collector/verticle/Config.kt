/*
 * Copyright 2021 Cyface GmbH
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

import com.google.auth.oauth2.GoogleCredentials
import de.cyface.collector.handler.auth.Authenticator
import de.cyface.collector.storage.DataStorageServiceBuilder
import de.cyface.collector.storage.cloud.GoogleCloudStorageServiceBuilder
import de.cyface.collector.storage.cloud.MongoDatabase
import de.cyface.collector.storage.gridfs.GridFsDao
import de.cyface.collector.storage.gridfs.GridFsStorageServiceBuilder
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.auth.mongo.MongoAuthentication
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions
import io.vertx.ext.mongo.MongoClient
import org.apache.commons.lang3.Validate
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Configuration parameters required to start the HTTP server which also handles routing.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 * @property vertx The Vertx instance to get the parameters from
 */
class Config(private val vertx: Vertx, private val config: JsonObject) {
    /**
     * The port on which the HTTP server should listen
     */
    val httpPort: Int
        get() {
            val httpPortValue = Parameter.HTTP_PORT.intValue(config, DEFAULT_HTTP_PORT)
            Validate.isTrue(
                httpPortValue > 0,
                "Invalid HTTP port $httpPortValue. Should be in the range 0 to 65535."
            )
            return httpPortValue
        }

    /**
     * The hostname of the HTTP server.
     */
    val host: String
        get() {
            val httpHostValue = Parameter.HTTP_HOST.stringValue(config)
            if (httpHostValue == null) {
                throw InvalidConfig(
                    """
                        HTTP host configuration missing. 
                        Please provide one using the Vertx configuration parameter ${Parameter.HTTP_HOST}
                    """.trimIndent()
                )
            }
            Validate.notEmpty(
                httpHostValue,
                """
                    Hostname was empty. 
                    Please provide a proper name via the Vertx parameter ${Parameter.HTTP_HOST}.
                """.trimIndent()
            )
            return httpHostValue
        }

    /**
     * The endpoint the HTTP server should listen on.
     */
    val endpoint: String
        get() {
            val httpEndpoint = HTTP_ENDPOINT.stringValue(config)
            if (httpEndpoint == null) {
                throw InvalidConfig(
                    """
                    HTTP endpoint configuration missing.
                    Please provide one using the Vertx configuration parameter ${Parameter.HTTP_ENDPOINT}
                    """.trimIndent()
                )
            }
            Validate.notEmpty(
                httpEndpoint,
                "Endpoint not found. Please use the parameter $httpEndpoint."
            )
            val builder = StringBuilder(httpEndpoint)
            val lastChar = httpEndpoint.last()
            if (lastChar == '/') {
                builder.append("*")
            } else if (lastChar != '*') {
                builder.append("/*")
            }
            return builder.toString()
        }

    /**
     * The client to use to access the Mongo database holding the user account data
     */
    val database: MongoClient
        get() {
            val databaseConfiguration = Parameter.MONGO_DB.jsonValue(config, JsonObject())
            val dataSourceName: String =
                databaseConfiguration.getString("data_source_name", DEFAULT_MONGO_DATA_SOURCE_NAME)
            return MongoClient.createShared(vertx, databaseConfiguration, dataSourceName)
        }

    /**
     * `null` or the Authenticator.kt that uses the Mongo user database to store and retrieve credentials.
     */
    val authProvider: MongoAuthentication
        get() {
            val authProperties = MongoAuthenticationOptions()
            return MongoAuthentication.create(database, authProperties)
        }

    /**
     * The public key used to check the validity of JWT tokens used for authentication
     */
    private val publicKey: String
        get() {
            val pathName = Parameter.JWT_PUBLIC_KEY_FILE_PATH.stringValue(config)
            if (pathName == null) {
                throw InvalidConfig("Configuration was missing path to public key file for JWT authentication.")
            }
            Validate.notEmpty(
                pathName,
                "Path to public key file for JWT authentication was empty. Please provide a valid location."
            )
            val keyFilePath = Path.of(pathName)

            if (keyFilePath.exists()) {
                return Files.readString(keyFilePath)
            } else {
                throw InvalidConfig("Public key file for JWT authentication missing at $keyFilePath.")
            }
        }

    /**
     * The private key used to issue new valid JWT tokens
     */
    private val privateKey: String
        get() {
            val pathName = Parameter.JWT_PRIVATE_KEY_FILE_PATH.stringValue(config)
            if (pathName == null) {
                throw InvalidConfig("Configuration was missing path to private key file for JWT authentication.")
            }
            Validate.notEmpty(
                pathName,
                "Path to private key file for JWT authentication was empty. Please provide a valid location."
            )
            val keyFilePath = Path.of(pathName)

            if (keyFilePath.exists()) {
                return Files.readString(keyFilePath)
            } else {
                throw InvalidConfig("Private key file for JWT authentication missing at $keyFilePath")
            }
        }

    /**
     * The auth provider to be used for authentication.
     */
    val jwtAuthProvider: JWTAuth
        get() {
            return JWTAuth.create(
                vertx,
                JWTAuthOptions()
                    .addPubSecKey(
                        PubSecKeyOptions()
                            .setAlgorithm(Authenticator.JWT_HASH_ALGORITHM)
                            .setBuffer(publicKey)
                    )
                    .addPubSecKey(
                        PubSecKeyOptions()
                            .setAlgorithm(Authenticator.JWT_HASH_ALGORITHM)
                            .setBuffer(privateKey)
                    )
            )
        }

    /**
     * The issuer to be used for authentication.
     */
    val issuer: String = "$host$endpoint"

    /**
     * The audience to be used for authentication.
     */
    val audience: String = "$host$endpoint"

    /**
     * A parameter telling the system how long a new JWT token stays valid in seconds.
     */
    val tokenExpirationTime: Int
        get() {
            val tokenExpirationTimeValue = Parameter.TOKEN_EXPIRATION_TIME.intValue(
                config,
                DEFAULT_TOKEN_VALIDATION_TIME
            )
            Validate.isTrue(tokenExpirationTimeValue > 0)
            return tokenExpirationTimeValue
        }

    /**
     * A parameter telling the system how large measurement uploads may be.
     */
    val measurementLimit: Long = Parameter.MEASUREMENT_PAYLOAD_LIMIT.longValue(config, DEFAULT_PAYLOAD_LIMIT.toLong())

    /**
     * A parameter telling the system the milliseconds to wait before removing cached uploads after last modification.
     */
    val uploadExpirationTime: Long
        get() {
            val uploadExpirationTimeValue = Parameter.UPLOAD_EXPIRATION_TIME.longValue(
                config,
                DEFAULT_UPLOAD_EXPIRATION_TIME
            )
            Validate.isTrue(uploadExpirationTimeValue > 0)
            return uploadExpirationTimeValue
        }

    val storageType: DataStorageServiceBuilder
        get() {
            val storageTypeConfig = config.getJsonObject("storage-type", JsonObject())
            val storageTypeString = storageTypeConfig.getString("type", "gridfs")
            val mongoClient = database
            when (storageTypeString) {
                "gridfs" -> {
                    val uploadFolder = Path.of(storageTypeConfig.getString("uploads-folder", "file-uploads/"))
                    if (!uploadFolder.exists()) {
                        val createdDirectory = Files.createDirectory(uploadFolder)
                        Validate.isTrue(createdDirectory.exists())
                    }
                    val fileSystem = vertx.fileSystem()
                    val dao = GridFsDao(mongoClient)
                    return GridFsStorageServiceBuilder(dao, fileSystem, uploadFolder)
                }
                "google" -> {
                    val collectionName = storageTypeConfig.getString("collection-name", "cyface-data")
                    val projectIdentifier = storageTypeConfig.getString("project-identifier")
                    val bucketName = storageTypeConfig.getString("bucket-name")
                    val credentialsFile = storageTypeConfig.getString("credentials-file")
                    val pagingSize = storageTypeConfig.getLong("paging-size", DEFAULT_GOOGLE_STORAGE_PAGING_SIZE)
                    val credentials = GoogleCredentials.fromStream(FileInputStream(credentialsFile))
                    val dao = MongoDatabase(mongoClient, collectionName)
                    return GoogleCloudStorageServiceBuilder(
                        credentials,
                        projectIdentifier,
                        bucketName,
                        dao,
                        vertx,
                        pagingSize
                    )
                }
                else -> throw InvalidConfig("Invalid storage type $storageTypeString!")
            }
        }

    /**
     * The username of the user initially created during application setup.
     * The default value is "admin".
     */
    val adminUserName: String
        get() {
            return Parameter.ADMIN_USER_NAME.stringValue(config, "admin")
        }

    /**
     * The password for the initial admin user.
     * The default value is "secret".
     * You must not use this value on production systems.
     */
    val adminPassword: String
        get() {
            return Parameter.ADMIN_PASSWORD.stringValue(config, "secret")
        }

    companion object {
        /**
         * Default value for an encryption salt. This must not be used in a production environment.
         */
        const val DEFAULT_CYFACE_SALT = "cyface-salt"

        /**
         * The number of bytes in one megabyte. This can be used to limit the amount of data accepted by the server.
         */
        private const val BYTES_IN_ONE_MEGABYTE = 1048576L

        /**
         * The default number of seconds the JWT authentication token is valid after login.
         */
        private val DEFAULT_PAYLOAD_LIMIT = Math.toIntExact(BYTES_IN_ONE_MEGABYTE * 100L)

        /**
         * The default number of milliseconds to wait before removing cached uploads after last modification: 7 days.
         */
        private const val DEFAULT_UPLOAD_EXPIRATION_TIME = (1000 * 60 * 60 * 24 * 7).toLong()

        /**
         * A parameter for the endpoint path the API service is available at.
         */
        private val HTTP_ENDPOINT = Parameter.HTTP_ENDPOINT

        private const val DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface"

        private const val DEFAULT_HTTP_PORT = 8080

        private const val DEFAULT_TOKEN_VALIDATION_TIME = 600

        private const val DEFAULT_GOOGLE_STORAGE_PAGING_SIZE = 100L
    }
}
