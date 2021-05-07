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

import de.cyface.api.ServerConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 * 
 * @author Klemens Muthmann
 * @version 2.0.2
 * @since 2.0.0
 */
public final class MainVerticle extends AbstractVerticle {

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {

        final var serverConfig = new ServerConfig(vertx);
        serverConfig.loadSalt(vertx).future().onComplete(result -> {
            if (result.failed()) {
                startFuture.fail(result.cause());
            } else {
                final String salt = result.result();
                serverConfig.lateInit(salt);
                deploy(startFuture, serverConfig);
            }
        });
    }

    /**
     * Deploys all required Verticles and tells the system when deployment has finished via the provided
     * <code>startFuture</code>.
     *  @param startFuture The future to complete or fail, depending on the success or failure of Verticle deployment.
     * @param serverConfig Configurations to be used by all verticles.
     */
    private void deploy(final Promise<Void> startFuture, final ServerConfig serverConfig) {
        final var config = config();
        final var verticleConfig = new DeploymentOptions().setConfig(config);
        final var collectorApiVerticle = new CollectorApiVerticle(serverConfig);
        final var managementApiVerticle = new ManagementApiVerticle(serverConfig.getSalt());

        // Start the collector API as first verticle.
        final var collectorApiFuture = vertx.deployVerticle(collectorApiVerticle, verticleConfig);

        // Start the management API as second verticle.
        final var managementApiFuture = vertx.deployVerticle(managementApiVerticle, verticleConfig);

        // As soon as both futures have a succeeded or one failed, finish the start up process.
        final var startUpProcessFuture = CompositeFuture.all(collectorApiFuture, managementApiFuture);
        startUpProcessFuture.onComplete(result -> {
            if (result.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(result.cause());
            }
        });
    }
}
