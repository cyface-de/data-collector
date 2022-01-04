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

import org.apache.commons.lang3.Validate;

import de.cyface.api.Parameter;
import de.cyface.api.EndpointConfig;
import de.cyface.api.Hasher;
import de.cyface.collector.handler.UserCreationHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
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
     * The salt used to encrypt user passwords on this server
     */
    private final String salt;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param salt The salt used to encrypt user passwords on this server
     */
    public ManagementApiVerticle(final String salt) {
        super();
        this.salt = salt;
    }

    @Override
    public void start(final Promise<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        final var mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(getVertx(),
                new JsonObject());

        final var client = EndpointConfig.createSharedMongoClient(getVertx(), mongoUserDatabaseConfiguration);
        final var hasher = new Hasher(HashingStrategy.load(), salt.getBytes(StandardCharsets.UTF_8));
        final var router = setupRouter(client, hasher);

        final var port = Parameter.MANAGEMENT_HTTP_PORT.intValue(getVertx(), 13_371);
        final var httpServer = new de.cyface.api.HttpServer(port);
        httpServer.start(vertx, router, startFuture);
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

        final var router = Router.router(getVertx());

        router.post("/user").handler(BodyHandler.create())
                .blockingHandler(new UserCreationHandler(mongoClient, "user", hasher));
        router.get("/*").handler(StaticHandler.create("webroot/management"));

        return router;
    }
}
