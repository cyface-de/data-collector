/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.vertx.core.Promise;
import org.apache.commons.lang3.Validate;

import de.cyface.api.ServerConfig;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;

/**
 * A user to test data exports for.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
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

        final String salt = "cyface-salt";
        final MongoAuth authProvider = ServerConfig.buildMongoAuthProvider(mongoClient, salt);
        mongoClient.findOne(DatabaseConstants.COLLECTION_USER,
                new JsonObject().put(DatabaseConstants.USER_USERNAME_FIELD, username),
                null, result -> {
                    if (result.failed()) {
                        resultHandler.handle(Future.failedFuture(result.cause()));
                    }
                    if (result.result() == null) {
                        authProvider.insertUser(username, password, roles, new ArrayList<>(),
                                userCreationResult -> {
                                    if (userCreationResult.succeeded()) {
                                        resultHandler.handle(Future.succeededFuture());
                                    } else {
                                        resultHandler.handle(Future.failedFuture(userCreationResult.cause()));
                                    }
                                });
                    } else {
                        resultHandler.handle(Future.failedFuture(new IllegalStateException(
                                "User already existent: " + result.result().encodePrettily())));
                    }
                });
    }
}
