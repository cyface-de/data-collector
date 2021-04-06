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

import java.nio.charset.StandardCharsets;

import de.cyface.collector.Parameter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * This Verticle starts the whole application, by deploying all required child Verticles.
 * 
 * @author Klemens Muthmann
 * @version 2.0.1
 * @since 2.0.0
 */
public final class MainVerticle extends AbstractVerticle {

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {

        loadSalt(vertx).future().onComplete(result -> {
            if (result.failed()) {
                startFuture.fail(result.cause());
            } else {
                deploy(startFuture, result.result());
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
    private void deploy(final Promise<Void> startFuture, final String salt) {
        final var config = config();
        final var verticleConfig = new DeploymentOptions().setConfig(config);
        final var collectorApiVerticle = new CollectorApiVerticle(salt);
        final var managementApiVerticle = new ManagementApiVerticle(salt);

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

    /**
     * Loads the external encryption salt from the Vertx configuration. If no value was provided the default value
     * "cyface-salt" is used.
     *
     * @param vertx The current <code>Vertx</code> instance
     * @return The <code>Promise</code> about a value to be used as encryption salt
     */
    private Promise<String> loadSalt(final Vertx vertx) {
        final Promise<String> result = Promise.promise();
        final var salt = Parameter.SALT.stringValue(vertx);
        final var saltPath = Parameter.SALT_PATH.stringValue(vertx);
        if (salt == null && saltPath == null) {
            result.complete("cyface-salt");
        } else if (salt != null && saltPath != null) {
            result.fail("Please provide either a salt value or a path to a salt file. "
                    + "Encountered both and can not decide which to use. Aborting!");
        } else if (salt != null) {
            result.complete(salt);
        } else {
            final var fileSystem = vertx.fileSystem();
            fileSystem.readFile(saltPath, readFileResult -> {
                if (readFileResult.failed()) {
                    result.fail(readFileResult.cause());
                } else {

                    final var loadedSalt = readFileResult.result().toString(StandardCharsets.UTF_8);
                    result.complete(loadedSalt);
                }
            });
        }
        return result;
    }

}
