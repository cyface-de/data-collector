/*
 * Copyright 2018-2023 Cyface GmbH
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

import de.cyface.collector.auth.OAuth2HandlerBuilder
import de.cyface.collector.configuration.Configuration
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.ext.auth.oauth2.OAuth2Options
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.2.0
 * @since 2.0.0
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

        val options = OAuth2Options()
            .setClientId(config.oauthConfig.client)
            .setClientSecret(config.oauthConfig.secret)
            .setSite(config.oauthConfig.site.toString())
            .setTenant(config.oauthConfig.tenant)
        val authHandlerBuilder = OAuth2HandlerBuilder(
            vertx,
            config.oauthConfig.callback,
            options
        )

        val collectorApiVerticle = CollectorApiVerticle(
            authHandlerBuilder,
            config.serviceHttpAddress,
            config.measurementPayloadLimit
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
}
