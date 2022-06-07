/*
 * Copyright 2022 Cyface GmbH
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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.cyface.api.model.Role;
import de.cyface.api.model.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * Tests that authorization of users belonging to different groups works as expected.
 * <p>
 * An integration test exists in `backend/exporter/AuthorizationTest` to avoid circular dependency.
 *
 * @author Armin Schnabel
 * @version 1.0.1
 * @since 6.4.0
 */
@ExtendWith(MockitoExtension.class)
public class AuthorizerTest {

    @Mock
    MongoAuthentication mockProvider;
    @Mock
    MongoClient mockDatabase;

    private final static String DEFAULT_USERNAME = "testUser";
    private final static ObjectId TEST_USER_ID = new ObjectId();
    private final static User TEST_USER = new User(TEST_USER_ID, DEFAULT_USERNAME);

    @ParameterizedTest
    @MethodSource("testParameters")
    void testLoadAccessibleUsers_forUser_withUser(final TestParameters parameters) {
        // Arrange
        final var oocut = new TestAuthorizer(mockProvider, mockDatabase);
        final var principal = new JsonObject();
        principal.put("username", DEFAULT_USERNAME);
        principal.put("roles", parameters.roles);
        final var user = new JsonObject().put("_id", TEST_USER_ID).put("username", DEFAULT_USERNAME).put("roles",
                parameters.roles);
        when(mockDatabase.findWithOptions(ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(JsonObject.class), ArgumentMatchers.any(FindOptions.class)))
                        .thenReturn(Future.succeededFuture(Collections.singletonList(user)));

        // Act
        final var result = oocut.loadAccessibleUsers(principal).result();

        // Assert
        assertThat(result, is(equalTo(parameters.expectedResult)));
    }

    @ParameterizedTest
    @MethodSource("testManagerRoles")
    void testLoadAccessibleUsers_forUser_withManager(final JsonArray roles) {
        // Arrange
        final var oocut = new TestAuthorizer(mockProvider, mockDatabase);
        final var principal = new JsonObject();
        principal.put("username", DEFAULT_USERNAME);
        principal.put("roles", roles);
        final var user = new JsonObject().put("_id", TEST_USER_ID).put("username", DEFAULT_USERNAME).put("roles",
                roles);
        when(mockDatabase.findWithOptions(ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(JsonObject.class), ArgumentMatchers.any(FindOptions.class)))
                        .thenReturn(Future.succeededFuture(Collections.singletonList(user)));

        // Act
        oocut.loadAccessibleUsers(principal);

        // Assert that the database is search for users of that project
        verify(mockDatabase, times(1)).find(eq(DatabaseConstants.COLLECTION_USER),
                eq(new JsonObject().put(DEFAULT_ROLE_FIELD, "project_user")));
    }

    @Test
    void testLoadAccessibleUsers_forGroupManager() {
        // Arrange
        final var oocut = new TestAuthorizer(mockProvider, mockDatabase);
        final var groupManager = new Role(Role.Type.GROUP_MANAGER, "project");
        final var roles = new JsonArray().add("project_manager");
        final var user = new JsonObject().put("_id", TEST_USER_ID).put("username", DEFAULT_USERNAME).put("roles",
                roles);
        when(mockDatabase.find(ArgumentMatchers.any(String.class), ArgumentMatchers.any(JsonObject.class)))
                .thenReturn(Future.succeededFuture(Collections.singletonList(user)));

        // Act
        oocut.loadAccessibleUsers(groupManager);

        // Assert
        verify(mockDatabase, times(1)).find(eq(DatabaseConstants.COLLECTION_USER),
                eq(new JsonObject().put(DEFAULT_ROLE_FIELD, "project_user")));
    }

    @SuppressWarnings("unused")
    static Stream<JsonArray> testManagerRoles() {
        return Stream.of(
                new JsonArray().add("project_manager"),
                new JsonArray().add("project_user").add("project_manager"));
    }

    @SuppressWarnings("unused")
    static Stream<TestParameters> testParameters() {
        return Stream.of(
                new TestParameters(new JsonArray().add("guest"), Collections.singleton(TEST_USER)),
                new TestParameters(new JsonArray().add("project_user"), Collections.singleton(TEST_USER)));
    }

    private static class TestParameters {
        JsonArray roles;
        Set<User> expectedResult;

        public TestParameters(JsonArray roles, Set<User> expectedResult) {
            this.roles = roles;
            this.expectedResult = expectedResult;
        }
    }

    private static class TestAuthorizer extends Authorizer {

        /**
         * Creates a new completely initialized instance of this class with access to all required authentication
         * information to authorize a request and fetch the correct data.
         *
         * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
         * @param mongoClient The Mongo user database containing all information about users
         */
        public TestAuthorizer(MongoAuthentication authProvider, MongoClient mongoClient) {
            super(authProvider, mongoClient, new PauseAndResumeAfterBodyParsing());
        }

        @Override
        protected void handleAuthorizedRequest(RoutingContext ctx, Set<User> users, MultiMap header) {
            // Nothing to do
        }
    }
}
