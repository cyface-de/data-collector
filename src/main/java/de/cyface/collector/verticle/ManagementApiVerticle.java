/*
 * Copyright 2018-2022 Cyface GmbH
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

import de.cyface.collector.configuration.Configuration;
import org.apache.commons.lang3.Validate;

import de.cyface.api.Hasher;
import de.cyface.collector.handler.UserCreationHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * A verticle that starts up an endpoint that may be used to create new user accounts for the Cyface data collector.
 * This is separated from the Upload API as we do not want to expose this unintentionally to the public.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 2.0.0
 */
public final class ManagementApiVerticle extends AbstractVerticle {

    /**
     * The application configuration to use for this verticle.
     */
    private final Configuration configuration;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param configuration The application configuration to use for this verticle.
     */
    public ManagementApiVerticle(final Configuration configuration) {
        super();
        this.configuration = configuration;
    }

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        final var databaseConfiguration = configuration.getMongoDb();

        final var client = MongoClient.createShared(
                vertx,
                databaseConfiguration,
                databaseConfiguration.getString("data_source_name")
        );
        final var saltCall = configuration.getSalt().bytes(vertx);
        saltCall.onSuccess(salt -> {
            final var hasher = new Hasher(HashingStrategy.load(), salt);
            final var router = setupRouter(client, hasher);

            final var port = configuration.getManagementHttpAddress().getPort();
            final var httpServer = new de.cyface.api.HttpServer(port);
            httpServer.start(vertx, router, startFuture);
        });
        saltCall.onFailure(startFuture::fail);
    }

    /**
     * Initializes the router used to provide the routes supported by the management API.
     *
     * @param mongoClient Client to a Mongo database used to check for valid user accounts used to generate new JWT
     *            token
     * @param hasher The hashing algorithm to obfuscate new passwords
     * @return The router initialized with the correct routes.
     */
    private Router setupRouter(final MongoClient mongoClient, final Hasher hasher) {
        Validate.notNull(mongoClient);

        final var userCreationHandler = new UserCreationHandler(mongoClient, "user", hasher);
        final var router = Router.router(getVertx());
        router.post("/user")
                .handler(BodyHandler.create())
                .handler(userCreationHandler);
        router.get("/*").handler(StaticHandler.create("webroot/management"));
        return router;
    }
}
