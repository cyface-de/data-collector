/*
 * Copyright 2020-2022 Cyface GmbH
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
package de.cyface.apitestutils.fixture.user;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import de.cyface.apitestutils.fixture.DatabaseConstants;
import de.cyface.apitestutils.fixture.MongoTestData;
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
 * Base class for users to test data exports for.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.0
 * @since 1.0.0
 */
public abstract class TestUser implements MongoTestData {
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
     * Creates a fully initialized instance of this class.
     *
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

        final var findUser = mongoClient.findOne(DatabaseConstants.COLLECTION_USER,
                new JsonObject().put(DatabaseConstants.USER_USERNAME_FIELD, username),
                null);
        findUser.onFailure(failure -> resultHandler.handle(Future.failedFuture(failure)));
        findUser.onSuccess(success -> {
            if (success == null) {
                final var insertCommand = insertCommand();
                final var insertUser = mongoClient.insert(DatabaseConstants.COLLECTION_USER, insertCommand);
                insertUser.onSuccess(inserted -> resultHandler.handle(Future.succeededFuture()));
                insertUser.onFailure(failure -> resultHandler.handle(Future.failedFuture(failure)));
            } else {
                resultHandler.handle(Future.failedFuture(new IllegalStateException(
                        "User already existent: " + success.encodePrettily())));
            }
        });
    }

    /**
     * @return The name of the user to be created.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The roles this user has.
     */
    protected List<String> getRoles() {
        return roles;
    }

    /**
     * @return The hashed and salted password of that user, as it would be present in the database.
     */
    protected String getHashedPassword() {
        final var salt = "cyface-salt";
        final var hasher = new Hasher(HashingStrategy.load(), salt.getBytes(StandardCharsets.UTF_8));
        return hasher.hash(password);
    }

    /**
     * @return A MongoDB command to insert the user into the test database.
     */
    protected abstract JsonObject insertCommand();
}
