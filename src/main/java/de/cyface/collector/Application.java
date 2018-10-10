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
 * @author muthmann
 *
 */
public class Application extends Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);
    private boolean metricsEnabled = false;

    public static void main(final String[] args) {
        new Application().dispatch(args);
    }

    @Override
    public void afterConfigParsed(final JsonObject config) {
        super.afterConfigParsed(config);
        metricsEnabled = config.getBoolean(Parameter.METRICS_ENABLED.key());
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
