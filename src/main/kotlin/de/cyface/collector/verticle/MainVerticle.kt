/*
 * Copyright 2018-2025 Cyface GmbH
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

import de.cyface.collector.auth.AuthHandlerBuilder
import de.cyface.collector.auth.JWKAuthHandlerBuilder
import de.cyface.collector.auth.OAuth2HandlerBuilder
import de.cyface.collector.configuration.Configuration
import de.cyface.collector.configuration.GoogleCloudStorageType
import de.cyface.collector.configuration.GridFsStorageType
import de.cyface.collector.configuration.InvalidConfig
import de.cyface.collector.configuration.LocalStorageType
import de.cyface.collector.configuration.StorageType
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.OAuth2Options
import io.vertx.ext.mongo.MongoClient
import io.vertx.kotlin.core.json.get
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.nio.file.Path

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
class MainVerticle : AbstractVerticle() {

    /**
     * Logger used by objects of this class. Configure it using "src/main/resources/logback.xml".
     */
    val logger: Logger = LoggerFactory.getLogger(MainVerticle::class.java)

    @Throws(Exception::class)
    override fun start(startFuture: Promise<Void>) {
        logger.info("Starting main verticle!")
        val jsonConfiguration = config()
        logger.debug("Active Configuration")
        logger.debug(jsonConfiguration.encodePrettily())
        val configuration = Configuration.deserialize(jsonConfiguration)
        try {
            deploy(startFuture, configuration)
        } catch (e: IOException) {
            startFuture.fail(e)
        } catch (e: RuntimeException) {
            startFuture.fail(e)
        }
    }

    /**
     * Deploys all required Verticles and tells the system when deployment has finished via the provided
     * `startFuture`.
     *
     * @param startFuture The future to complete or fail, depending on the success or failure of Verticle deployment.
     * @param config The application configuration for this Verticle.
     * @throws IOException if key files are inaccessible.
     */
    @Throws(IOException::class)
    private fun deploy(startFuture: Promise<Void>, config: Configuration) {
        logger.info("Deploying main verticle!")

        val authHandlerBuilder = config.authConfig.getString("type").let {
            authHandlerBuilder(it, config)
        }
        val mongoClient = MongoClient.createShared(
            vertx,
            config.mongoDb,
            config.mongoDb.getString("data_source_name")
        )

        val storageTypeConfig = config.storageTypeJson
        val dataStorageType = storageType(storageTypeConfig)

        val serverConfiguration = ServerConfiguration(
            config.httpPort,
            config.httpPath,
            config.measurementPayloadLimit,
            config.uploadExpiration,
        )
        val storageServiceBuilder = dataStorageType.dataStorageServiceBuilder(
            vertx,
            mongoClient
        )

        val collectorApiVerticle = CollectorApiVerticle(
            authHandlerBuilder,
            serverConfiguration,
            storageServiceBuilder
        )

        // Start the collector API as first verticle.
        val collectorDeployment = vertx.deployVerticle(collectorApiVerticle)

        // As soon the future has succeeded or one failed, finish the startup process.
        collectorDeployment.onSuccess {
            logger.info("Successfully deployed main Verticle!")
            startFuture.complete()
        }
        collectorDeployment.onFailure { cause: Throwable ->
            logger.error("Failed deploying main Verticle!", cause)
            startFuture.fail(cause)
        }
    }

    private fun authHandlerBuilder(
        string: String?,
        config: Configuration
    ): AuthHandlerBuilder = when (string) {
        "oauth" -> {
            val options = OAuth2Options()
                .setClientId(config.authConfig.getString("client"))
                .setClientSecret(config.authConfig.getString("secret"))
                .setSite(config.authConfig.getString("site"))
                .setTenant(config.authConfig.getString("tenant"))
            OAuth2HandlerBuilder(
                vertx,
                URL(config.authConfig.getString("callback")),
                options
            )
        }

        "jwt" -> {
            val jwkJson = config.authConfig.getJsonObject("jwk")
            JWKAuthHandlerBuilder(vertx, jwkJson)
        }

        else -> throw InvalidConfig("Invalid auth type $string!")
    }

    private fun storageType(storageTypeConfig: JsonObject): StorageType =
        when (val storageTypeString = storageTypeConfig.getString("type")) {
            "gridfs" -> {
                val uploadFolder = Path.of(storageTypeConfig.getString("uploads-folder", "file-uploads/"))
                GridFsStorageType(uploadFolder)
            }

            "google" -> {
                val collectionName = storageTypeConfig.get<String>("collection-name")
                val projectIdentifier = storageTypeConfig.get<String>("project-identifier")
                val bucketName = storageTypeConfig.get<String>("bucket-name")
                val credentialsFile = storageTypeConfig.get<String>("credentials-file")
                val bufferSize = storageTypeConfig.get<Int>("buffer-size")
                GoogleCloudStorageType(
                    collectionName,
                    projectIdentifier,
                    bucketName,
                    credentialsFile,
                    bufferSize
                )
            }

            "local" -> {
                LocalStorageType()
            }

            null -> throw InvalidConfig(
                """
                Storage type configuration missing. Please provide either a Google or GridFS Storage type.
                """.trimIndent()
            )

            else -> throw InvalidConfig("Invalid storage type $storageTypeString!")
        }
}
