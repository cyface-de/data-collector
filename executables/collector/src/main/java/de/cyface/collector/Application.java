/*
 * Copyright 2018 Cyface GmbH
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

import de.cyface.api.InvalidConfiguration;
import de.cyface.api.Parameter;
import de.cyface.collector.verticle.MainVerticle;
import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An object of this class forms the entry point to the Cyface data collector application. It contains the
 * <code>main</code> method, which you can start to run everything. However you need to provide the {@link MainVerticle}
 * as a parameter to this class using <code>run de.cyface.collector.verticle.MainVerticle</code>.
 * <p>
 * You may also provide additional parameters in JSON format as described in the <code>README.md</code> file.
 * <p>
 * This class allows to set up things which need to be set up before the vehicle start, like logging.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.4
 * @since 2.0.0
 */
public class Application extends Launcher {

    /**
     * The logger used for objects of this class. Change its configuration using
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    /**
     * The flag specifying whether metrics measurement is enabled or not.
     */
    private transient boolean metricsEnabled = false;

    /**
     * Starts the application.
     *
     * @param args See README.adoc and documentation of the Vert.x <code>Launcher</code> class, for further details
     *             about supported arguments.
     */
    public static void main(final String[] args) {
        System.setProperty(
                io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME,
                SLF4JLogDelegateFactory.class.getName());

        new Application().dispatch(args);
    }

    @Override
    public final void afterConfigParsed(final JsonObject config) {
        super.afterConfigParsed(config);

        checkValidConfiguration(config);

        if (config.containsKey(Parameter.METRICS_ENABLED.key())) {
            metricsEnabled = config.getBoolean(Parameter.METRICS_ENABLED.key());
        }
    }

    @Override
    public final void beforeStartingVertx(final VertxOptions options) {
        if (metricsEnabled) {
            LOGGER.info("Enabling metrics capturing to prometheus!");
            options.setMetricsOptions(new MicrometerMetricsOptions()
                    .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true).setStartEmbeddedServer(true)
                            .setEmbeddedServerOptions(new HttpServerOptions().setPort(8081))
                            .setEmbeddedServerEndpoint("/metrics"))
                    .setEnabled(true));
        } else {
            LOGGER.info("Starting without capturing metrics");
        }
    }

    /**
     * Checks if the provided configuration is valid for a Cyface data collector.
     *
     * @param config The configuration to check.
     */
    private void checkValidConfiguration(final JsonObject config) {
        if (config.getString(Parameter.HTTP_HOST.key()) == null
                || config.getString(Parameter.HTTP_ENDPOINT.key()) == null) {
            throw new InvalidConfiguration(config);
        }
    }
}
