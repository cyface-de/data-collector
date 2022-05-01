/*
 * Copyright (C) 2020-2022 Cyface GmbH - All Rights Reserved
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.cyface.api.model.Role;
import de.cyface.api.model.User;
import de.cyface.apitestutils.TestMongoDatabase;
import de.cyface.apitestutils.fixture.user.DirectTestUser;
import de.cyface.apitestutils.fixture.user.TestUser;
import io.vertx.core.CompositeFuture;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests that authorization of users belonging to different groups works as expected.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.2
 * @since 6.6.0
 */
@ExtendWith(VertxExtension.class)
public class AuthorizationTest {

    /**
     * A Mongo database which is started before each test and destroyed afterwards, so each test gets a fresh instance.
     */
    private TestMongoDatabase testMongoDatabase;
    /**
     * The object of the class under test to call the appropriate methods.
     */
    private TestAuthorizer oocut;
    /**
     * Shared MongoDB client to be used to access the databases.
     */
    private MongoClient databaseClient;
    /**
     * Default group name in this test.
     */
    private final static String DEFAULT_GROUP = "test-group";

    /**
     * Initialize an empty Mongo test database and a fresh instance of the object of the class under test.
     *
     * @param vertx A Vertx instance used to set up the system
     * @param testContext The Vertx test context used to synchronize this method with the JUnit lifecycle
     * @throws IOException If the Mongo database fails to start for some reason
     */
    @BeforeEach
    public void setUp(final Vertx vertx, final VertxTestContext testContext) throws IOException {

        testMongoDatabase = new TestMongoDatabase();
        testMongoDatabase.start();
        databaseClient = EndpointConfig.createSharedMongoClient(vertx, testMongoDatabase.config());

        final var mongoAuth = AuthenticatedEndpointConfig.buildMongoAuthProvider(databaseClient);

        oocut = new TestAuthorizer(mongoAuth, databaseClient);

        testContext.completeNow();
    }

    /**
     * Destroys the Mongo test database.
     */
    @AfterEach
    public void tearDown() {
        testMongoDatabase.stop();
    }

    /**
     * A parameterized test, running queries to a test group on different sets of users in a Mongo database.
     *
     * @param env The environment for one run of this test
     * @param testContext The Vertx test context used to control the Vertx framework during testing
     */
    @DisplayName("Check users provided on group request")
    @ParameterizedTest(name = "Testing ==> {0}")
    @MethodSource("testUsers")
    public void test_loadAccessibleUsers_ofGroupManager(final TestEnvironment env, final VertxTestContext testContext) {

        // Arrange
        final var testUsers = env.getTestUsers();
        // Wait for all async insert operations
        final var inserts = CompositeFuture
                .all(testUsers.stream().map(u -> u.insert(databaseClient)).collect(Collectors.toList()));

        // Act
        inserts.onComplete(testContext.succeeding(
                dataInserted -> {
                    final var groupManager = new Role(Role.Type.GROUP_MANAGER, DEFAULT_GROUP);
                    final var loadUsers = oocut.loadAccessibleUsers(groupManager);
                    loadUsers.onSuccess(users -> testContext.verify(() -> {

                        // Assert
                        final var expectedUsernames = env.getExpectedUsernames();
                        assertThat(users, hasSize(expectedUsernames.size()));
                        assertThat(users.stream().map(User::getName).collect(Collectors.toList()),
                                hasItems(expectedUsernames.toArray(new String[0])));
                        testContext.completeNow();
                    }));
                    loadUsers.onFailure(testContext::failNow);
                }));
    }

    /**
     * A test which ensures both the group manager itself and the group users can be accessed by the manager.
     *
     * @param testContext The Vertx test context used to control the Vertx framework during testing
     */
    @Test
    public void test_loadAccessibleUsers_ofPrincipal(final VertxTestContext testContext) {

        // Arrange
        final var groupUser = new DirectTestUser(DEFAULT_GROUP + 1, "secret",
                DEFAULT_GROUP + DatabaseConstants.USER_GROUP_ROLE_SUFFIX);
        final var groupManager = new DirectTestUser(DEFAULT_GROUP, "secret",
                DEFAULT_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX);
        final var testUsers = List.of(new TestUser[] {groupUser, groupManager});

        // Wait for all async insert operations
        final var inserts = CompositeFuture
                .all(testUsers.stream().map(u -> u.insert(databaseClient)).collect(Collectors.toList()));

        // Act
        inserts.onComplete(testContext.succeeding(
                dataInserted -> {
                    final var principal = new JsonObject();
                    principal.put("username", groupManager.getUsername());
                    principal.put("roles",
                            new JsonArray().add(DEFAULT_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX));
                    final var loadUsers = oocut.loadAccessibleUsers(principal);
                    loadUsers.onSuccess(users -> testContext.verify(() -> {

                        // Assert
                        final var expectedUsernames = List
                                .of(new String[] {groupUser.getUsername(), groupManager.getUsername()});
                        assertThat(users, hasSize(expectedUsernames.size()));
                        assertThat(users.stream().map(User::getName).collect(Collectors.toList()),
                                hasItems(expectedUsernames.toArray(new String[0])));
                        testContext.completeNow();
                    }));
                    loadUsers.onFailure(testContext::failNow);
                }));
    }

    /**
     * The method providing the test parameters.
     *
     * @return A stream of test fixtures and expected results, used as test parameters
     */
    static Stream<TestEnvironment> testUsers() {
        final var guestUser = new DirectTestUser("test", "secret", DatabaseConstants.GUEST_ROLE);
        final var groupUser = new DirectTestUser(DEFAULT_GROUP + 1, "secret",
                DEFAULT_GROUP + DatabaseConstants.USER_GROUP_ROLE_SUFFIX);
        final var groupManager = new DirectTestUser(DEFAULT_GROUP, "secret",
                DEFAULT_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX);
        final var groupManagerAndUser = new DirectTestUser("groupManagerAndUser", "secret",
                DEFAULT_GROUP + DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX,
                DEFAULT_GROUP + DatabaseConstants.USER_GROUP_ROLE_SUFFIX);

        return Stream.of(
                // When no user exists for the requested group, no user should be returned
                new TestEnvironment(Collections.emptyList(), Collections.emptyList()),
                // When only a guest user exists, no user should be returned
                new TestEnvironment(Collections.singletonList(guestUser), Collections.emptyList()),
                // Normal happy path should return exactly the matching user
                new TestEnvironment(Collections.singletonList(groupUser),
                        Collections.singletonList(groupUser.getUsername())),
                // User with only the manager role is not returned
                new TestEnvironment(Collections.singletonList(groupManager), Collections.emptyList()),
                // User with both manager and user role is successfully returned
                new TestEnvironment(Collections.singletonList(groupManagerAndUser),
                        Collections.singletonList(groupManagerAndUser.getUsername())),
                // Both, group_manager and group_user exists, only group user should be returned
                // Authorized.loadAccessibleUsers(principal{=groupManager}) will however return itself (manager), too
                new TestEnvironment(List.of(new TestUser[] {groupUser, groupManager}),
                        List.of(new String[] {groupUser.getUsername()})));
    }

    /**
     * A test environment groups together the values required for a single run of the parameterized test implemented by
     * {@link AuthorizationTest#test_loadAccessibleUsers_ofGroupManager(TestEnvironment, VertxTestContext)}.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    static class TestEnvironment {
        /**
         * The test fixture of users to load into the test database.
         */
        private final List<TestUser> testUsers;
        /**
         * The usernames expected to be returned upon a query with the test group.
         */
        private final List<String> expectedUsernames;

        /**
         * Creates a new completely initialized instance of this class. All properties are read only.
         *
         * @param testUsers The test fixture of users to load into the test database
         * @param expectedUsernames The usernames expected to be returned upon a query with the test group
         */
        public TestEnvironment(final List<TestUser> testUsers, final List<String> expectedUsernames) {
            Validate.notNull(testUsers);
            Validate.notNull(expectedUsernames);

            this.testUsers = new ArrayList<>(testUsers);
            this.expectedUsernames = new ArrayList<>(expectedUsernames);
        }

        /**
         * @return The test fixture of users to load into the test database
         */
        public List<TestUser> getTestUsers() {
            return new ArrayList<>(testUsers);
        }

        /**
         * @return The usernames expected to be returned upon a query with the test group
         */
        public List<String> getExpectedUsernames() {
            return new ArrayList<>(expectedUsernames);
        }

        @Override
        public String toString() {
            return "TestEnvironment{" + "testUsers=" + testUsers + ", expectedUsernames=" + expectedUsernames + '}';
        }
    }

    private static class TestAuthorizer extends Authorizer {

        /**
         * Creates a new completely initialized instance of this class with access to all required authentication
         * information to authorize a request and fetch the correct data.
         *
         * @param authProvider An auth provider used by this server to authenticate against the Mongo user database
         * @param mongoUserDatabase The Mongo user database containing all information about users
         */
        public TestAuthorizer(final MongoAuthentication authProvider, final MongoClient mongoUserDatabase) {
            super(authProvider, mongoUserDatabase, false);
        }

        @Override
        protected void handleAuthorizedRequest(final RoutingContext ctx, final List<User> users,
                final MultiMap header) {
            // Nothing to do
        }
    }
}
