/**
 * 
 */
package de.cyface.collector;

import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;

/**
 * An object of this class forms the entry point to the Cyface data collector application. It contains the
 * <code>main</code> method, which you can start to run everything. However you need to provide the {@link MainVerticle}
 * as a parameter to this class using <code>run de.cyface.collector.MainVerticle</code>.
 * <p>
 * You may also provide additional parameters in JSON format as described in the <code>README.md</code> file.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public class Application extends Launcher {

    /**
     * The logger used for objects of this class. Change its configuration using
     * <code>src/main/resources/vertx-default-jul-logging.properties</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    /**
     * The flag specifying whether metrics measurement is enabled or not.
     */
    private boolean metricsEnabled = false;

    /**
     * Starts the application.
     * 
     * @param args See README.adoc and documenation of the Vert.x <code>Launcher</code> class, for further details about supported arguments.
     */
    public static void main(final String[] args) {
        new Application().dispatch(args);
    }

    @Override
    public void afterConfigParsed(final JsonObject config) {
        super.afterConfigParsed(config);
        if (config.containsKey(Parameter.METRICS_ENABLED.key())) {
            metricsEnabled = config.getBoolean(Parameter.METRICS_ENABLED.key());
        }
    }

    @Override
    public void beforeStartingVertx(final VertxOptions options) {
        if (metricsEnabled) {
            options.setMetricsOptions(new MicrometerMetricsOptions()
                    .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true).setStartEmbeddedServer(true)
                            .setEmbeddedServerOptions(new HttpServerOptions().setPort(8081))
                            .setEmbeddedServerEndpoint("/metrics"))
                    .setEnabled(true));
        }
    }
}
