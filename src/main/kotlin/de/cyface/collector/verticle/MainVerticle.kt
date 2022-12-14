/*
 * Copyright 2018-2022 Cyface GmbH
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

import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.CompositeFuture
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.1.1
 * @since 2.0.0
 */
class MainVerticle : AbstractVerticle() {

    /**
     * Logger used by objects of this class. Configure it using "src/main/resources/logback.xml".
     */
    val logger = LoggerFactory.getLogger(MainVerticle::class.java)

    @Throws(Exception::class)
    override fun start(startFuture: Promise<Void>) {
        logger.info("Starting main verticle!")
        val configuration = Config(vertx, config())
        val loadSaltCall = loadSalt(config())
        loadSaltCall.onSuccess { salt: String ->
            logger.info("Loaded salt!")
            try {
                deploy(startFuture, salt, configuration)
            } catch (e: IOException) {
                startFuture.fail(e)
            } catch (e: RuntimeException) {
                startFuture.fail(e)
            }
        }
        loadSaltCall.onFailure { cause: Throwable? ->
            logger.error("Failed loading salt", cause)
            startFuture.fail(cause)
        }
    }

    /**
     * Deploys all required Verticles and tells the system when deployment has finished via the provided
     * `startFuture`.
     *
     * @param startFuture The future to complete or fail, depending on the success or failure of Verticle deployment.
     * @param salt The value to be used as encryption salt.
     * @param config The application configuration for this Verticle.
     * @throws IOException if key files are inaccessible.
     */
    @Throws(IOException::class)
    private fun deploy(startFuture: Promise<Void>, salt: String, config: Config) {
        logger.info("Deploying main verticle!")
        val verticleConfig = DeploymentOptions().setConfig(config())

        val collectorApiVerticle = CollectorApiVerticle(salt, config)
        val managementApiVerticle = ManagementApiVerticle(salt)

        // Start the collector API as first verticle.
        val collectorDeployment = vertx.deployVerticle(collectorApiVerticle, verticleConfig)

        // Start the management API as second verticle.
        val managementDeployment = vertx.deployVerticle(managementApiVerticle, verticleConfig)

        // As soon as both futures have a succeeded or one failed, finish the startup process.
        val startUp = CompositeFuture.all(collectorDeployment, managementDeployment)
        startUp.onSuccess {
            logger.info("Successfully deployed main Verticle!")
            startFuture.complete()
        }
        startUp.onFailure { cause: Throwable ->
            logger.error("Failed deploying main Verticle!", cause)
            startFuture.fail(cause)
        }
    }

    /**
     * Loads the external encryption salt from the Vertx configuration. If no value was provided the default value
     * `#DEFAULT_CYFACE_SALT` is used.
     *
     *
     * The salt is only needed to generate a hash, not to check a password against a hash as the salt is stored at the
     * beginning of the hash. This way the salt can be changed without invalidating all previous hashes.
     *
     *
     * Asynchronous implementation as in Vert.X you can only access files asynchronously.
     *
     * @param config The current `Vertx` configuration
     * @return The `Future` which resolves to a value to be used as encryption salt if successful
     */
    private fun loadSalt(config: JsonObject): Future<String> {
        val result = Promise.promise<String>()
        val salt = Parameter.SALT.stringValue(config)
        val saltPath = Parameter.SALT_PATH.stringValue(config)
        if (salt == null && saltPath == null) {
            result.complete(Config.DEFAULT_CYFACE_SALT)
        } else if (salt != null && saltPath != null) {
            result.fail(
                "Please provide either a salt value or a path to a salt file. " +
                    "Encountered both and can not decide which to use. Aborting!"
            )
        } else if (salt != null) {
            result.complete(salt)
        } else {
            val fileSystem = vertx.fileSystem()
            fileSystem.readFile(
                saltPath
            ) { readFileResult: AsyncResult<Buffer> ->
                if (readFileResult.failed()) {
                    result.fail(readFileResult.cause())
                } else {
                    val loadedSalt =
                        readFileResult.result().toString(StandardCharsets.UTF_8)
                    result.complete(loadedSalt)
                }
            }
        }
        return result.future()
    }
}
