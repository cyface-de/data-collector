/*
 * Copyright (C) 2022 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.api;

import java.util.List;
import java.util.stream.Collectors;

import de.cyface.api.model.User;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * Loads {@link User}s from the database for a vert.x context.
 *
 * @author Armin Schnabel
 * @since 6.6.0
 */
public class UserRetriever {

    /**
     * The name of the collection in the Mongo database used to store {@link User}s.
     */
    private final String collectionName;
    /**
     * The query which defines which data to load.
     */
    private final JsonObject query;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param collectionName The name of the collection in the Mongo database used to store {@link User}s.
     * @param username The username of the user to load.
     */
    public UserRetriever(final String collectionName, final String username) {

        this.query = new JsonObject().put("username", username);
        this.collectionName = collectionName;
    }

    /**
     * Loads the specified {@link User}s from the database.
     *
     * @param mongoClient The client to access the data from.
     * @return a {@code Future} containing the {@link User}s if successful.
     */
    public Future<List<User>> load(final MongoClient mongoClient) {

        final Promise<List<User>> promise = Promise.promise();
        final var fields = new JsonObject().put("_id", 1).put("username", 1);
        final var options = new FindOptions().setFields(fields);

        final var find = mongoClient.findWithOptions(collectionName, query, options);
        find.onSuccess(result -> {
            final var users = result.stream().map(User::new).collect(Collectors.toList());
            promise.complete(users);
        });
        find.onFailure(promise::fail);

        return promise.future();
    }
}
