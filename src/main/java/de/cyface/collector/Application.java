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
package de.cyface.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.vertx.micrometer.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.verticle.MainVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

import java.util.EnumSet;

// ATTENTION: This class must not be converted to Kotlin. As a Kotlin class it does not call the correct main method.
/**
 * An object of this class forms the entry point to the Cyface data collector application. It contains the
 * <code>main</code> method, which you can start to run everything. However, you need to provide the
 * {@link MainVerticle}
 * as a parameter to this class using <code>run de.cyface.collector.verticle.MainVerticle</code>.
 * <p>
 * You may also provide additional parameters in JSON format as described in the <code>README.md</code> file.
 * <p>
 * This class allows setting up things which need to be set up before the Verticle start, like logging.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.5
 * @since 2.0.0
 */
public class Application extends Launcher {

    /**
     * The logger used for objects of this class. Change its configuration using
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    /**
     * Port used by Prometheus to request logging information from this server.
     */
    private static final int PROMETHEUS_SERVER_PORT = 8081;
    /**
     * The flag specifying whether metrics measurement is enabled or not.
     */
    private transient boolean metricsEnabled = false;

    /**
     * Starts the application.
     *
     * @param args See README.adoc and documentation of the Vert.x <code>Launcher</code> class, for further details
     *            about supported arguments.
     */
    public static void main(final String[] args) {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                SLF4JLogDelegateFactory.class.getName());

        new Application().dispatch(args);
    }

    @Override
    public final void afterConfigParsed(final JsonObject config) {
        super.afterConfigParsed(config);

        metricsEnabled = config.getBoolean("metrics.enabled", false);
    }

    @Override
    public final void beforeStartingVertx(final VertxOptions options) {
        if (metricsEnabled) {
            LOGGER.info("Enabling metrics capturing to prometheus!");
            var micrometerOptions = new MicrometerMetricsOptions()
                    .setEnabled(true)
                    .setLabels(EnumSet.of(
                            Label.HTTP_METHOD, // z.B. GET, POST
                            Label.HTTP_CODE,   // z.B. 200, 404, 500
                            Label.HTTP_PATH,   // The called path
                            Label.EB_ADDRESS,  // The event bus address
                            Label.POOL_TYPE,   // Thread-Pool Type (i.e. worker)
                            Label.POOL_NAME    // Thread-Pool Name
                    ));
            options.setMetricsOptions(micrometerOptions
                    .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true).setStartEmbeddedServer(true)
                            .setEmbeddedServerOptions(new HttpServerOptions().setPort(PROMETHEUS_SERVER_PORT))
                            .setEmbeddedServerEndpoint("/metrics"))
                    .setEnabled(true));
        } else {
            LOGGER.info("Starting without capturing metrics");
        }
    }

    @Override
    public final void afterStartingVertx(final Vertx vertx) {
        if (metricsEnabled) {
            // Bind JVM metrics to the Vert.x Micrometer registry (not the global one)
            // so they appear on the /metrics endpoint served by the embedded Prometheus server.
            MeterRegistry registry = BackendRegistries.getDefaultNow();
            if (registry != null) {
                new JvmMemoryMetrics().bindTo(registry);
                new JvmGcMetrics().bindTo(registry);
                new JvmThreadMetrics().bindTo(registry);
                new ProcessorMetrics().bindTo(registry);
                LOGGER.info("JVM metrics bound to Vert.x Prometheus registry");
            } else {
                LOGGER.warn("Could not bind JVM metrics: Vert.x metrics registry not available");
            }
        }
    }
}