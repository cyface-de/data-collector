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
package de.cyface.collector

import io.vertx.core.Launcher
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.SLF4JLogDelegateFactory
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import org.slf4j.LoggerFactory

/**
 * Starts the application.
 *
 * @param args See README.adoc and documentation of the Vert.x `Launcher` class, for further details
 * about supported arguments.
 */
fun main(args: Array<String>) {
    System.setProperty(
        "vertx.logger-delegate-factory-class-name",
        SLF4JLogDelegateFactory::class.java.getName()
    )
    Application().dispatch(args)
}

/**
 * An object of this class forms the entry point to the Cyface data collector application. It contains the
 * `main` method, which you can start to run everything. However, you need to provide the
 * [de.cyface.collector.verticle.MainVerticle]
 * as a parameter to this class using `run de.cyface.collector.verticle.MainVerticle`.
 *
 *
 * You may also provide additional parameters in JSON format as described in the `README.md` file.
 *
 *
 * This class allows setting up things which need to be set up before the Verticle start, like logging.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
class Application : Launcher() {
    /**
     * The flag specifying whether metrics measurement is enabled or not.
     */
    @Transient
    private var metricsEnabled = false

    override fun afterConfigParsed(config: JsonObject) {
        super.afterConfigParsed(config)
        metricsEnabled = config.getBoolean("metrics.enabled")
    }

    override fun beforeStartingVertx(options: VertxOptions) {
        if (metricsEnabled) {
            LOGGER.info("Enabling metrics capturing to prometheus!")
            options.setMetricsOptions(
                MicrometerMetricsOptions()
                    .setPrometheusOptions(
                        VertxPrometheusOptions()
                            .setEnabled(true)
                            .setStartEmbeddedServer(true)
                            .setEmbeddedServerOptions(
                                HttpServerOptions().setPort(PROMETHEUS_SERVER_PORT)
                            )
                            .setEmbeddedServerEndpoint("/metrics")
                    )
                    .setEnabled(true)
            )
        } else {
            LOGGER.info("Starting without capturing metrics")
        }
    }

    companion object {
        /**
         * The logger used for objects of this class. Change its configuration using
         * `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(Application::class.java)

        /**
         * Port used by Prometheus to request logging information from this server.
         */
        private const val PROMETHEUS_SERVER_PORT = 8081


    }
}
