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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 * 
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.0.0
 */
public final class MainVerticle extends AbstractVerticle {
    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        // Create a few futures to synchronize the start up process
        Future<Void> collectorApiFuture = Future.future();
        Future<Void> managementApiFuture = Future.future();
        CompositeFuture startUpProcessFuture = CompositeFuture.all(collectorApiFuture, managementApiFuture);

        // Start the collector API as first verticle.
        vertx.deployVerticle(CollectorApiVerticle.class, new DeploymentOptions().setConfig(config()), result -> {
            if (result.succeeded()) {
                collectorApiFuture.complete();
            } else {
                collectorApiFuture.fail(result.cause());
            }
        });

        // Start the management API as second verticle.
        vertx.deployVerticle(ManagementApiVerticle.class, new DeploymentOptions().setConfig(config()), result -> {
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
