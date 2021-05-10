/*
 * Copyright 2020-2021 Cyface GmbH
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
package de.cyface.apitestutils.fixture;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.Validate;

import de.cyface.api.Hasher;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.mongo.MongoClient;

/**
 * A user to test data exports for.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.0
 * @since 1.0.0
 */
public final class TestUser implements MongoTestData {
    /**
     * The name of the user to be created.
     */
    private final String username;
    /**
     * The roles this user has.
     */
    private final List<String> roles;
    /**
     * The password used to authenticate the user.
     */
    private final String password;

    /**
     * @param username The name of the user to be created
     * @param roles The roles this user has
     * @param password The password used to authenticate the user
     */
    public TestUser(final String username, final String password, final String... roles) {
        Validate.notEmpty(username);
        Validate.notEmpty(roles);
        Validate.notEmpty(password);

        this.username = username;
        this.roles = Arrays.asList(roles);
        this.password = password;
    }

    @Override
    public void insert(final MongoClient mongoClient, final Handler<AsyncResult<Void>> resultHandler) {
        Validate.notNull(mongoClient);
        Validate.notNull(resultHandler);

        final var salt = "cyface-salt";
        mongoClient.findOne(DatabaseConstants.COLLECTION_USER,
                new JsonObject().put(DatabaseConstants.USER_USERNAME_FIELD, username),
                null, result -> {
                    if (result.failed()) {
                        resultHandler.handle(Future.failedFuture(result.cause()));
                    }
                    if (result.result() == null) {
                        final var hasher = new Hasher(HashingStrategy.load(),
                                salt.getBytes(StandardCharsets.UTF_8));
                        final var hashedPassword = hasher.hash(password);
                        final var newUserInsertCommand = new JsonObject().put("username", username)
                                .put("roles", new JsonArray(roles)).put("password", hashedPassword);
                        final var userInsertedFuture = mongoClient.insert(DatabaseConstants.COLLECTION_USER,
                                newUserInsertCommand);
                        userInsertedFuture.onSuccess(success -> resultHandler.handle(Future.succeededFuture()));
                        userInsertedFuture.onFailure(failure -> resultHandler.handle(Future.failedFuture(failure)));
                    } else {
                        resultHandler.handle(Future.failedFuture(new IllegalStateException(
                                "User already existent: " + result.result().encodePrettily())));
                    }
                });
    }
}
