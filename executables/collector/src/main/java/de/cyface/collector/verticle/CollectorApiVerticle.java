/*
 * Copyright 2018-2021 Cyface GmbH
 * This file is part of the Cyface Data Collector.
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.verticle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.cyface.api.FailureHandler;
import de.cyface.api.Parameter;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.api.Authenticator;
import de.cyface.api.ServerConfig;
import de.cyface.api.Hasher;
import de.cyface.collector.handler.MeasurementHandler;
import de.cyface.collector.handler.UserCreationHandler;
import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 2.0.0
 */
public final class CollectorApiVerticle extends AbstractVerticle {

    /**
     * The <code>Logger</code> used for objects of this class. Configure it by changing the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectorApiVerticle.class);
    /**
     * The role which identifies users with "admin" privileges.
     */
    private static final String ADMIN_ROLE = "admin";
    private static final String MEASUREMENTS_ENDPOINT = "/measurements";
    /**
     * The value to be used as encryption salt
     */
    private final String salt;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param salt The value to be used as encryption salt
     * @throws IOException if key files are inaccessible
     */
    public CollectorApiVerticle(final String salt) throws IOException {
        super();
        this.salt = salt;
    }

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        // Setup Measurement event bus
        prepareEventBus();

        // Start http server
        final Promise<Void> serverStartPromise = Promise.promise();
        final ServerConfig serverConfig = new ServerConfig(vertx);
        setupRoutes(serverConfig, result -> {
            if (result.succeeded()) {
                final var router = result.result();
                final var httpServer = new de.cyface.api.HttpServer(serverConfig.getHttpPort());
                httpServer.start(vertx, router, serverStartPromise);
            } else {
                serverStartPromise.fail(result.cause());
            }
        });

        // Insert default admin user
        final var adminUsername = Parameter.ADMIN_USER_NAME.stringValue(vertx, "admin");
        final var adminPassword = Parameter.ADMIN_PASSWORD.stringValue(vertx, "secret");
        final var defaultUserCreatedFuture = Promise.promise();
        final var userClient = serverConfig.getUserDatabase();
        userClient.findOne("user", new JsonObject().put("username", adminUsername), null, result -> {
            if (result.failed()) {
                defaultUserCreatedFuture.fail(result.cause());
                return;
            }
            if (result.result() == null) {
                final var hasher = new Hasher(HashingStrategy.load(),
                        salt.getBytes(StandardCharsets.UTF_8));
                final var userCreationHandler = new UserCreationHandler(userClient, "user", hasher);
                userCreationHandler.createUser(adminUsername, adminPassword, ADMIN_ROLE, success -> {
                    LOGGER.info("Identifier of new user id: {}", success);
                    defaultUserCreatedFuture.complete();
                }, failure -> {
                    LOGGER.error("Unable to create default user!");
                    defaultUserCreatedFuture.fail(failure);
                });
            } else {
                defaultUserCreatedFuture.complete();
            }
        });

        // Block until all futures completed
        final var startUpFinishedFuture = CompositeFuture.all(serverStartPromise.future(),
                defaultUserCreatedFuture.future());
        startUpFinishedFuture.onComplete(r -> {
            if (r.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(r.cause());
            }
        });
    }

    /**
     * Prepares the Vertx event bus during startup of the application.
     */
    private void prepareEventBus() {
        vertx.eventBus().registerDefaultCodec(Measurement.class, Measurement.getCodec());
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     *
     * @param serverConfig HTTP server configuration parameters required to setup the routes
     * @param next The handler to call when the router has been created.
     */
    private void setupRoutes(final ServerConfig serverConfig, final Handler<AsyncResult<Router>> next) {
        Validate.notNull(next);

        // Setup 'v2/api' route
        final var mainRouter = Router.router(vertx);
        final var v2ApiRouter = Router.router(vertx);
        mainRouter.mountSubRouter(serverConfig.getEndpoint(), v2ApiRouter);

        // Setup measurement routes
        final var measurementHandler = new MeasurementHandler(serverConfig);
        final var failureHandler = ErrorHandler.create(vertx);

        // Setup authentication
        Authenticator.setupAuthentication("/login", v2ApiRouter, serverConfig)
                .addAuthenticatedHandler(MEASUREMENTS_ENDPOINT, measurementHandler, failureHandler);

        // Setup web-api route
        v2ApiRouter.route().handler(StaticHandler.create("webroot/api"));

        // Setup unknown-resource handler
        mainRouter.route("/*").last().handler(new FailureHandler());

        // Only requires a `future` because we add the OpenApi generated route. Else this would be synchronous.
        next.handle(Future.succeededFuture(mainRouter));
    }
}
