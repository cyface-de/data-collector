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
package de.cyface.collector.handler;

import java.util.ArrayList;

import org.apache.commons.lang3.Validate;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that creates users inside the user Mongo database.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class UserCreationHandler implements Handler<RoutingContext> {

    /**
     * The logger used for objects of this class. Configure it by modifying <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCreationHandler.class);

    /**
     * An authenticator that uses credentials from the Mongo user database to authenticate users.
     */
    private final transient MongoAuth mongoAuth;

    /**
     * Creates a new completely initialized <code>UserCreationHandler</code>.
     * 
     * @param mongoAuth An authenticator that uses credentials from the Mongo user database to authenticate users.
     */
    public UserCreationHandler(final MongoAuth mongoAuth) {
        Validate.notNull(mongoAuth);

        this.mongoAuth = mongoAuth;
    }

    @Override
    public void handle(final RoutingContext event) {
        final JsonObject body = event.getBodyAsJson();
        final String username = body.getString("username");
        final String password = body.getString("password");

        mongoAuth.insertUser(username, password, new ArrayList<>(), new ArrayList<>(), ir -> {
            if (ir.succeeded()) {
                LOGGER.info("Added new user with id: " + username);
                event.response().setStatusCode(201).end();
            } else {
                LOGGER.error("Unable to create user with id: " + username, ir.cause());
                event.fail(400);
            }
        });

    }

}
