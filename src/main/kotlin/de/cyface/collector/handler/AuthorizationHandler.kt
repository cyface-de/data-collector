package de.cyface.collector.handler

import de.cyface.api.Authorizer
import de.cyface.api.DatabaseConstants
import de.cyface.api.PauseAndResumeStrategy
import de.cyface.api.UserRetriever
import de.cyface.api.model.Role
import de.cyface.api.model.User
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.mongo.MongoAuthentication
import io.vertx.ext.auth.mongo.MongoAuthorization
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.RoutingContext
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

/**
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property authProvider An auth provider used by this server to authenticate against the Mongo user database.
 * @property mongoDatabase The database to check which users a {@code manager} user has access to.
 * @property strategy The pause and resume strategy to be used which wraps async calls in {@link #handle(RoutingContext)}.
 */
class AuthorizationHandler(
    private val authProvider: MongoAuthentication,
    private val mongoDatabase: MongoClient,
    private val strategy: PauseAndResumeStrategy): Handler<RoutingContext> {
    override fun handle(context: RoutingContext) {
        try {
            LOGGER.info("Received new request.")
            val request: HttpServerRequest = context.request()
            val headers = request.headers()
            LOGGER.debug("Request headers: {}", headers)

            // Check authorization
            val principal: JsonObject = context.user().principal()
            val username = principal.getString(MongoAuthorization.DEFAULT_USERNAME_FIELD)

            // Before async operations, pause request body parsing to not lose the body or protocol upgrades.
            strategy.pause(request)
            val authentication: Future<io.vertx.ext.auth.User> = authProvider.authenticate(principal)
            authentication.onSuccess { user: io.vertx.ext.auth.User ->
                try {
                    strategy.resume(request)

                    // Before async operations, pause request body parsing to not lose the body or protocol upgrades.
                    strategy.pause(request)
                    // Load principal from authentication result, so it also contains the roles
                    val loadUsers: Future<Set<User>> =
                        loadAccessibleUsers(user.principal())
                    loadUsers.onSuccess { accessibleUsers: Set<User> ->
                        try {
                            strategy.resume(request)
                            LOGGER.trace(
                                "Request for {} authorized to access users {}",
                                username,
                                accessibleUsers
                            )
                            context.put("accessible-users", accessibleUsers).next()
                            //handleAuthorizedRequest(context, accessibleUsers, headers)
                        } catch (e: RuntimeException) {
                            context.fail(e)
                        }
                    }
                    loadUsers.onFailure { e: Throwable ->
                        LOGGER.error("Loading accessible users failed for user {}", username, e)
                        context.fail(500, e)
                    }
                } catch (e: RuntimeException) {
                    context.fail(500, e)
                }
            }
            authentication.onFailure { e: Throwable ->
                LOGGER.error("Authorization failed for user {}!", username)
                context.fail(401, e)
            }
        } catch (e: NumberFormatException) {
            LOGGER.error("Data was not parsable!")
            context.fail(Authorizer.ENTITY_UNPARSABLE, e)
        }
    }

    /**
     * Loads all users which the authenticated `User` can access.
     *
     *
     * If the user is not a group manager, only the user itself is returned.
     *
     *
     * If the user holds a [DatabaseConstants.GROUP_MANAGER_ROLE_SUFFIX] role, identifying it as the manager of
     * that group, all users of that group are loaded from the user collection.
     *
     *
     * This method is only public because of the `backend/AuthorizationTest`, see last comment on CY-5720.
     *
     * @param principal The principal object of the authenticated `User` which requests user data
     * @return The ids of the users which the `user` can access
     */
    private fun loadAccessibleUsers(principal: JsonObject): Future<Set<User>> {
        val promise = Promise.promise<Set<User>>()

        // Load id of authenticated user
        val username = Validate.notEmpty(principal.getString(MongoAuthorization.DEFAULT_USERNAME_FIELD))
        val loadUser = UserRetriever("user", username).load(mongoDatabase)
        loadUser.onSuccess { users: List<User> ->
            try {
                Validate.isTrue(users.size == 1)
                val user = users[0]

                // Guest users and group users can only access their own account
                val roles =
                    principal.getJsonArray(MongoAuthorization.DEFAULT_ROLE_FIELD).stream().map { r: Any? ->
                        Role(
                            r as String?
                        )
                    }
                        .collect(Collectors.toList())
                val managerRoles = roles.stream()
                    .filter { r: Role -> r.type == Role.Type.GROUP_MANAGER }
                    .collect(Collectors.toList())
                if (managerRoles.isEmpty()) {
                    promise.complete(setOf(user))
                } else if (managerRoles.size > 1) {
                    promise.fail(
                        IllegalArgumentException(
                            String.format(
                                "User %s is manager of more than one group.",
                                user
                            )
                        )
                    )
                } else {
                    val groupManager = managerRoles[0]
                    val loadUsers =
                        loadAccessibleUsers(groupManager)
                    loadUsers.onSuccess { groupUsers: List<User> ->
                        try {
                            val ret = HashSet(groupUsers)
                            ret.add(user)
                            promise.complete(ret)
                        } catch (e: RuntimeException) {
                            promise.fail(e)
                        }
                    }
                    loadUsers.onFailure { cause: Throwable -> promise.fail(cause) }
                }
            } catch (e: RuntimeException) {
                promise.fail(e)
            }
        }
        loadUser.onFailure { cause: Throwable -> promise.fail(cause) }
        return promise.future()
    }

    /**
     * Loads all users a specific group manager can access.
     *
     * @param groupManager The `Role` group manager to load the users for
     * @return The [User]s which the `groupManager` can access
     */
    private fun loadAccessibleUsers(groupManager: Role): Future<List<User>> {
        Validate.isTrue(groupManager.type == Role.Type.GROUP_MANAGER)
        Validate.notEmpty(groupManager.group)
        val promise = Promise.promise<List<User>>()
        val groupUsersRole = groupManager.group + DatabaseConstants.USER_GROUP_ROLE_SUFFIX
        val query = JsonObject().put(MongoAuthorization.DEFAULT_ROLE_FIELD, groupUsersRole)
        val loadUsers: Future<List<JsonObject>> = mongoDatabase.find(DatabaseConstants.COLLECTION_USER, query)
        loadUsers.onSuccess { result: List<JsonObject> ->
            val users =
                result.parallelStream().map { databaseValue: JsonObject ->
                    User(
                        databaseValue
                    )
                }
                    .collect(Collectors.toList())
            promise.complete(users)
        }
        loadUsers.onFailure { cause: Throwable -> promise.fail(cause) }
        return promise.future()
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by adapting the values in
         * <code>src/main/resources/logback.xml</code>.
         */
        private val LOGGER = LoggerFactory.getLogger(Authorizer::class.java)
    }
}