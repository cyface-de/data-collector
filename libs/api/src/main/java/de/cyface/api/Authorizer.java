/*
 * Copyright 2019-2022 Cyface GmbH
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
package de.cyface.api;

import static io.vertx.ext.auth.mongo.MongoAuthorization.DEFAULT_ROLE_FIELD;
import static io.vertx.ext.auth.mongo.MongoAuthorization.DEFAULT_USERNAME_FIELD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.api.model.Role;
import de.cyface.api.model.User;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * Abstract base class for all requests to an endpoint which requires authorization.
 * <p>
 * This class ensures that such requests are properly authorized.
 *
 * @author Armin Schnabel
 * @version 2.0.1
 * @since 6.4.0
 */
public abstract class Authorizer implements Handler<RoutingContext> {

    /**
     * The logger for objects of this class. You can change its configuration by adapting the values in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Authorizer.class);
    /**
     * Http code which indicates that the upload request syntax was incorrect.
     */
    public static final int ENTITY_UNPARSABLE = 422;
    /**
     * Http code which indicates that the request is not authorized.
     */
    public static final int UNAUTHORIZED = 401;
    /**
     * An auth provider used by this server to authenticate against the Mongo user database
     */
    private final MongoAuthentication authProvider;
    /**
     * The database to check which users a {@code manager} user has access to.
     */
    private final MongoClient mongoDatabase;
    /**
     * The pause and resume strategy to be used which wraps async calls in {@link #handle(RoutingContext)}.
     */
    private final PauseAndResumeStrategy strategy;

    /**
     * Creates a new completely initialized instance of this class with access to all required authentication
     * information to authorize a request and fetch the correct data.
     *
     * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
     * @param mongoDatabase The Mongo database containing all information about users
     * @param strategy The pause and resume strategy to be used which wraps async calls in
     *            {@link #handle(RoutingContext)}.
     */
    public Authorizer(final MongoAuthentication authProvider, final MongoClient mongoDatabase,
            final PauseAndResumeStrategy strategy) {
        Validate.notNull(authProvider);
        Validate.notNull(mongoDatabase);

        this.authProvider = authProvider;
        this.mongoDatabase = mongoDatabase;
        this.strategy = strategy;
    }

    @Override
    public void handle(final RoutingContext context) {
        try {
            LOGGER.info("Received new request.");
            final var request = context.request();
            final var headers = request.headers();
            LOGGER.debug("Request headers: {}", headers);

            // Check authorization
            final var principal = context.user().principal();
            final var username = principal.getString(DEFAULT_USERNAME_FIELD);

            // Before async operations, pause request body parsing to not lose the body or protocol upgrades.
            strategy.pause(request);
            final var authentication = authProvider.authenticate(principal);
            authentication.onSuccess(user -> {
                try {
                    strategy.resume(request);

                    // Before async operations, pause request body parsing to not lose the body or protocol upgrades.
                    strategy.pause(request);
                    // Load principal from authentication result, so it also contains the roles
                    final var loadUsers = loadAccessibleUsers(user.principal());
                    loadUsers.onSuccess(accessibleUsers -> {
                        try {
                            strategy.resume(request);
                            LOGGER.trace("Request for {} authorized to access users {}", username, accessibleUsers);
                            handleAuthorizedRequest(context, accessibleUsers, headers);
                        } catch (RuntimeException e) {
                            context.fail(e);
                        }
                    });
                    loadUsers.onFailure(e -> {
                        LOGGER.error("Loading accessible users failed for user {}", username, e);
                        context.fail(500, e);
                    });
                } catch (final RuntimeException e) {
                    context.fail(500, e);
                }
            });
            authentication.onFailure(e -> {
                LOGGER.error("Authorization failed for user {}!", username);
                context.fail(401, e);
            });
        } catch (final NumberFormatException e) {
            LOGGER.error("Data was not parsable!");
            context.fail(ENTITY_UNPARSABLE, e);
        }
    }

    /**
     * This is the method that should be implemented by subclasses to carry out the business logic on an authorized
     * request.
     * 
     * @param ctx Vert.x request context
     * @param users A list of usernames for which the request is authorized to export data
     * @param header the header of the request which may contain parameters required to process the request.
     */
    protected abstract void handleAuthorizedRequest(final RoutingContext ctx, final List<User> users,
            final MultiMap header);

    /**
     * Loads all users which the authenticated {@code User} can access.
     * <p>
     * If the user is not a group manager, only the user itself is returned.
     * <p>
     * If the user holds a {@link DatabaseConstants#GROUP_MANAGER_ROLE_SUFFIX} role, identifying it as the manager of
     * that group, all users of that group are loaded from the user collection.
     * <p>
     * This method is only public because of the `backend/AuthorizationTest`, see last comment on CY-5720.
     *
     * @param principal The principal object of the authenticated {@code User} which requests user data
     * @return The ids of the users which the {@code user} can access
     */
    public Future<List<User>> loadAccessibleUsers(final JsonObject principal) {
        final Promise<List<User>> promise = Promise.promise();

        // Load id of authenticated user
        final var username = Validate.notEmpty(principal.getString(DEFAULT_USERNAME_FIELD));
        final var loadUser = new UserRetriever("user", username).load(mongoDatabase);
        loadUser.onSuccess(users -> {
            try {
                Validate.isTrue(users.size() == 1);
                final var user = users.get(0);

                // Guest users and group users can only access their own account
                final var roles = principal.getJsonArray(DEFAULT_ROLE_FIELD).stream().map(r -> new Role((String)r))
                        .collect(Collectors.toList());
                final var managerRoles = roles.stream().filter(r -> r.getType().equals(Role.Type.GROUP_MANAGER))
                        .collect(Collectors.toList());
                if (managerRoles.isEmpty()) {
                    promise.complete(Collections.singletonList(user));
                } else if (managerRoles.size() > 1) {
                    promise.fail(new IllegalArgumentException(
                            String.format("User %s is manager of more than one group.", user)));
                } else {
                    final var groupManager = managerRoles.get(0);
                    final var loadUsers = loadAccessibleUsers(groupManager);
                    loadUsers.onSuccess(groupUsers -> {
                        try {
                            final var ret = new ArrayList<>(groupUsers);
                            ret.add(user);
                            promise.complete(ret);
                        } catch (RuntimeException e) {
                            promise.fail(e);
                        }
                    });
                    loadUsers.onFailure(promise::fail);
                }
            } catch (RuntimeException e) {
                promise.fail(e);
            }
        });
        loadUser.onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Loads all users a specific group manager can access.
     * <p>
     * This method is only public because of the `backend/AuthorizationTest`, see last comment on CY-5720.
     *
     * @param groupManager The {@code Role} group manager to load the users for
     * @return The {@link User}s which the {@code groupManager} can access
     */
    public Future<List<User>> loadAccessibleUsers(final Role groupManager) {
        Validate.isTrue(groupManager.getType().equals(Role.Type.GROUP_MANAGER));
        Validate.notEmpty(groupManager.getGroup());

        final Promise<List<User>> promise = Promise.promise();
        final var groupUsersRole = groupManager.getGroup() + DatabaseConstants.USER_GROUP_ROLE_SUFFIX;
        final var query = new JsonObject().put(DEFAULT_ROLE_FIELD, groupUsersRole);
        final var loadUsers = mongoDatabase.find(DatabaseConstants.COLLECTION_USER, query);
        loadUsers.onSuccess(result -> {
            final var users = result.parallelStream().map(User::new).collect(Collectors.toList());
            promise.complete(users);
        });
        loadUsers.onFailure(promise::fail);
        return promise.future();
    }

    @SuppressWarnings("unused") // API
    public MongoAuthentication getAuthProvider() {
        return authProvider;
    }

    public MongoClient getMongoDatabase() {
        return mongoDatabase;
    }
}
