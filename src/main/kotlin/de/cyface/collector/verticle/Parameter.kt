package de.cyface.collector.verticle

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * An enumeration of parameters, that may be provided upon application startup, to configure the application.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.0.0
 * @property key The parameter key used to load its value from the JSON configuration.
 */
enum class Parameter(private val key: String) {
    /**
     * The location of the PEM file containing the private key to issue new JWT tokens.
     */
    JWT_PRIVATE_KEY_FILE_PATH("jwt.private"),

    /**
     * The location of the PEM file containing the public key to check JWT tokens for validity.
     */
    JWT_PUBLIC_KEY_FILE_PATH("jwt.public"),

    /**
     * A parameter setting the hostname the API service is available at.
     */
    HTTP_HOST("http.host"),

    /**
     * A parameter for the endpoint path the API V3 service is available at.
     */
    HTTP_ENDPOINT("http.endpoint"),

    /**
     * The server port the API shall be available at.
     */
    HTTP_PORT("http.port"),

    /**
     * Detailed connection information about the Mongo database. This database stores all the credentials of users
     * capable of logging in to the systems and all data received via the REST-API. This should be a JSON object with
     * supported parameters explained at:
     * [https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client]
     * (https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client).
     */
    MONGO_DB("mongo.db"),

    /**
     * The username of a default administration user created on system start.
     */
    ADMIN_USER_NAME("admin.user"),

    /**
     * The password for the default administration user created on system start.
     */
    ADMIN_PASSWORD("admin.password"),

    /**
     * Salt used by the Mongo authentication provider to encrypt all user passwords.
     */
    SALT("salt"),

    /**
     * Path to a file containing the salt used to encrypt passwords in the database.
     */
    SALT_PATH("salt.path"),

    /**
     * A parameter telling the system how long a new JWT token stays valid in seconds.
     */
    TOKEN_EXPIRATION_TIME("jwt.expiration"),

    /**
     * A parameter telling the system the milliseconds to wait before removing cached uploads after last modification.
     */
    UPLOAD_EXPIRATION_TIME("upload.expiration"),

    /**
     * A parameter telling the system how large measurement uploads may be.
     */
    MEASUREMENT_PAYLOAD_LIMIT("measurement.payload.limit"),

    /**
     * The server port the management interface for the API shall be available at.
     */
    MANAGEMENT_HTTP_PORT("http.port.management"),

    /**
     * A parameter telling the system, whether it should publish metrics using Micrometer to Prometheus or not.
     */
    METRICS_ENABLED("metrics.enabled");

    /**
     * The logger used for objects of this class. You can change its configuration by changing the values in
     * `src/main/resources/vertx-default-jul-logging.properties`.
     */
    private val logger = LoggerFactory.getLogger(Parameter::class.java)

    /**
     * Provides the string value of this parameter from the Vert.x configuration or the `defaultValue` if
     * there was none.
     *
     * @param config The Vertx configuration to load the value from.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as a `String` or the `defaultValue`.
     */
    open fun stringValue(config: JsonObject, defaultValue: String): String {
        val value = config.getString(key)
        val ret = value ?: defaultValue
        logger.info("Using configuration value: {} for key: {}.", ret, key)
        return ret
    }

    /**
     * Provides the string value of this parameter from the Vert.x configuration or the `null` if there was
     * none.
     *
     * @param config The Vertx configuration to load the value from.
     * @return Either the value of the parameter as a `String` or `null`.
     */
    open fun stringValue(config: JsonObject): String? {
        val ret = config.getString(key)
        logger.info("Using configuration value: {} for key: {}.", ret, key)
        return ret
    }

    /**
     * Provides the integer value of this parameter from the Vert.x configuration or the `defaultValue` if
     * there was none.
     *
     * @param config The Vertx configuration to load the value from.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as an `int` or the `defaultValue`.
     * @throws ClassCastException If the value was not an integer.
     */
    open fun intValue(config: JsonObject, defaultValue: Int): Int {
        val value = config.getInteger(key)
        val ret = value ?: defaultValue
        logger.info("Using configuration value: {} for key: {}.", ret, key)
        return ret
    }

    /**
     * Provides the lon value of this parameter from the Vert.x configuration or the `defaultValue` if
     * there was none.
     *
     * @param config The Vertx configuration to load the value from.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as an `int` or the `defaultValue`.
     * @throws ClassCastException If the value was not a long.
     */
    open fun longValue(config: JsonObject, defaultValue: Long): Long {
        val value = config.getLong(key)
        val ret = value ?: defaultValue
        logger.info("Using configuration value: {} for key: {}.", ret, key)
        return ret
    }

    /**
     * Provides the JSON value of this parameter from the Vert.x configuration or the `defaultValue` if there
     * was none.
     *
     * @param config The Vertx configuration to load the value from.
     * @param defaultValue A default value if the parameter was missing.
     * @return Either the value of the parameter as a JSON object or the `defaultValue`.
     */
    open fun jsonValue(config: JsonObject, defaultValue: JsonObject): JsonObject {
        val value = config.getJsonObject(key)
        val ret = value ?: defaultValue
        logger.info("Read json value {} for key: {}.", ret, key)
        return ret
    }

    override fun toString(): String {
        return key
    }

    /**
     * Provide this parameters value as a boolean.
     *
     * @param config The Vertx configuration to load the value from.
     * @param defaultValue The default value to return if the key was not found in the configuration.
     * @return Either the requested value or the default value if the requested one was not found.
     */
    open fun boolValue(config: JsonObject, defaultValue: Boolean): Boolean {
        val value = config.getBoolean(key)
        val ret = value ?: defaultValue
        logger.info("Read json value {} for key: {}.", value, key)
        return ret
    }
}
