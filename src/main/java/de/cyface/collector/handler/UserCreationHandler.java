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

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.Hasher;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that creates users inside the user Mongo database.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.1
 * @since 2.0.0
 */
public final class UserCreationHandler implements Handler<RoutingContext> {

    /**
     * The logger used for objects of this class. Configure it by modifying <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCreationHandler.class);
    /**
     * This is the role which a newly created user gets when it's created without any defined roles.
     */
    private static final String DEFAULT_USER_ROLE = "guest";

    /**
     * A <code>MongoClient</code> used to store user credentials.
     */
    private final MongoClient mongoClient;
    private final String userCollectionName;
    private final Hasher passwordHasingStrategy;

    /**
     * Creates a new completely initialized <code>UserCreationHandler</code>.
     *
     * @param mongoClient A <code>MongoClient</code> used to store user credentials
     */
    public UserCreationHandler(final MongoClient mongoClient, final String userCollectionName,
            final Hasher passwordHashingStrategy) {
        Validate.notNull(mongoClient);

        this.mongoClient = mongoClient;
        this.userCollectionName = userCollectionName;
        this.passwordHasingStrategy = passwordHashingStrategy;
    }

    @Override
    public void handle(final RoutingContext event) {
        final var body = event.getBodyAsJson();
        final var username = body.getString("username");
        final var password = body.getString("password");
        final var providedRole = body.getString("role");
        final var role = providedRole == null || providedRole.isEmpty() ? DEFAULT_USER_ROLE : providedRole;

        createUser(username, password, role, success -> {
            LOGGER.info("Added new user with id: {}", username);
            event.response().setStatusCode(201).end();
        }, failure -> {
            LOGGER.error(String.format("Unable to create user with id: %s", username), failure);
            event.fail(400);
        });
    }

    public void createUser(final String username, final String password, final String role,
            final Handler<String> onSuccess, final Handler<Throwable> onFailure) {
        final var hashedPassword = passwordHasingStrategy.hash(password);
        final var newUserInsertCommand = new JsonObject().put("username", username)
                .put("roles", new JsonArray().add(role)).put("password", hashedPassword);
        final var userInsertedFuture = mongoClient.insert(userCollectionName, newUserInsertCommand);
        userInsertedFuture.onSuccess(onSuccess);
        userInsertedFuture.onFailure(onFailure);
    }
}
