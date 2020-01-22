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

import org.apache.commons.lang3.Validate;

import de.cyface.collector.MongoDbUtils;
import de.cyface.collector.Parameter;
import de.cyface.collector.handler.UserCreationHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * A verticle that starts up an endpoint that may be used to create new user accounts for the Cyface data collector.
 * This is separated from the Upload API as we do not want to expose this unintentionally to the public.
 *
 * @author Klemens Muthmann
 * @version 1.0.2
 * @since 2.0.0
 */
public final class ManagementApiVerticle extends AbstractVerticle {
    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        final JsonObject mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(getVertx(),
                new JsonObject());
        // if (mongoUserDatabaseConfiguration == null) {
        // startFuture.fail("There was no configuration to access a user database. Please provide one by setting "
        // + Parameter.MONGO_USER_DB.key()
        // + " to the correct value. Please refer to the README for further instructions.");
        // } else {
        final int port = Parameter.MANAGEMENT_HTTP_PORT.intValue(getVertx(), 13_371);
        final String salt = Parameter.SALT.stringValue(vertx, "cyface-salt");

        final MongoClient client = MongoDbUtils.createSharedMongoClient(getVertx(), mongoUserDatabaseConfiguration);
        final MongoAuth mongoAuth = MongoDbUtils.buildMongoAuthProvider(client, salt);
        final Router router = setupRouter(mongoAuth);
        startHttpServer(startFuture, router, port);
        // }
    }

    /**
     * Initializes the router used to provide the routes supported by the management API.
     *
     * @param mongoAuth Authentication provider used to check for valid user accounts used to generate new JWT token.
     * @return The router initialized with the correct routes.
     */
    private Router setupRouter(final MongoAuth mongoAuth) {
        Validate.notNull(mongoAuth);

        final Router router = Router.router(getVertx());

        router.post("/user").handler(BodyHandler.create()).blockingHandler(new UserCreationHandler(mongoAuth));
        router.get("/*").handler(StaticHandler.create("webroot/management"));

        return router;
    }

    /**
     * Starts the HTTP server providing the management API.
     *
     * @param startFuture A future that will be informed about success or failure of the start up process.
     * @param router The router containing the routes (i.e. HTTP endpoints) provided by the server.
     * @param port The port the management API will be available at.
     */
    private void startHttpServer(final Future<Void> startFuture, final Router router, final int port) {
        Validate.notNull(startFuture);
        Validate.notNull(router);
        Validate.isTrue(port > 0);

        getVertx().createHttpServer().requestHandler(router).listen(port, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(res.cause());
            }
        });
    }
}
