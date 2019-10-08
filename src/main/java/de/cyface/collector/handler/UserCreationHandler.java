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

import com.mongodb.lang.NonNull;
import org.apache.commons.lang3.Validate;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.RoutingContext;

import javax.validation.constraints.NotNull;

/**
 * A handler that creates users inside the user Mongo database.
 * 
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
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
    private transient final MongoAuth mongoAuth;

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
        final String providedUsername = body.getString("username");
        final String password = body.getString("password");
        final int numberOfUsers = body.getInteger("numberOfUsers");
        Validate.isTrue(numberOfUsers > 0 && numberOfUsers <= 10_000);

        // Create users
        final boolean[] success = {true};
        if (numberOfUsers == 1) {
            success[0] = insertUser(providedUsername, password);
        } else {
            for (int i = 1; i <= numberOfUsers; i++) {
                final String username = providedUsername + i;
                final boolean isSuccessful = insertUser(username, password);
                success[0] = success[0] && isSuccessful;
            }
        }

        // Response
        if (success[0]) {
            event.response().setStatusCode(201).end();
        } else {
            event.fail(400);
        }
    }

    private boolean insertUser(final String username, final String password) {
        final boolean[] success = {true};
        mongoAuth.insertUser(username, password, new ArrayList<>(), new ArrayList<>(), ir -> {
            if (ir.succeeded()) {
                LOGGER.info("Added new user with id: " + username);
            } else {
                LOGGER.error("Unable to create user with id: " + username, ir.cause());
                success[0] = false;
            }
        });
        return success[0];
    }
}
