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
package de.cyface.collector.verticle;

import java.nio.charset.Charset;

import de.cyface.collector.Parameter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 * 
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
public final class MainVerticle extends AbstractVerticle {

    /**
     * Logger used for objects of this class. Configure it using <code>/src/main/resource/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(final Future<Void> startFuture) throws Exception {

        final String saltPath = Parameter.SALT_PATH.stringValue(vertx, "secrets/salt");
        vertx.fileSystem().exists(saltPath, result -> {
            if (result.failed()) {
                LOGGER.error(result.cause());
                startFuture.fail(result.cause());
            }

            if (result.result()) {
                vertx.fileSystem().readFile(saltPath, readSaltResult -> {
                    if (readSaltResult.failed()) {
                        LOGGER.error(result.cause());
                        startFuture.fail(result.cause());
                    }

                    final String salt = readSaltResult.result().toString(Charset.defaultCharset());
                    deploy(startFuture, salt);
                });
            } else {
                // Deploy with default salt.
                deploy(startFuture, "cyface-salt");
            }
        });

    }

    /**
     * Deploys all required Verticles and tells the system when deployment has finished via the provided
     * <code>startFuture</code>.
     * 
     * @param startFuture The future to complete or fail, depending on the success or failure of Verticle deployment.
     * @param salt Salt to use by all Verticles to encrypt and decrypt user passwords.
     */
    private void deploy(final Future<Void> startFuture, final String salt) {
        // Create a few futures to synchronize the start up process
        final Future<Void> collectorApiFuture = Future.future();
        final Future<Void> managementApiFuture = Future.future();
        final var startUpProcessFuture = CompositeFuture.all(collectorApiFuture, managementApiFuture);
        final var config = config();
        config.put(Parameter.SALT.key(), salt);
        final var verticleConfig = new DeploymentOptions().setConfig(config);

        // Start the collector API as first verticle.
        vertx.deployVerticle(CollectorApiVerticle.class, verticleConfig, result -> {
            if (result.succeeded()) {
                collectorApiFuture.complete();
            } else {
                collectorApiFuture.fail(result.cause());
            }
        });

        // Start the management API as second verticle.
        vertx.deployVerticle(ManagementApiVerticle.class, verticleConfig, result -> {
            if (result.succeeded()) {
                managementApiFuture.complete();
            } else {
                managementApiFuture.fail(result.cause());
            }
        });

        // As soon as both futures have a succeeded or one failed, finish the start up process.
        startUpProcessFuture.setHandler(result -> {
            if (result.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(result.cause());
            }
        });
    }

}
