package de.cyface.collector.handler.auth

import de.cyface.collector.configuration.Configuration
import de.cyface.collector.configuration.InvalidConfig
import io.vertx.core.Vertx
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.auth.mongo.MongoAuthentication
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions
import io.vertx.ext.mongo.MongoClient
import org.apache.commons.lang3.Validate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * A class containing the information required to setup the collectors authentication system, using JWT token.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class AuthenticationConfiguration(
    private val configuration: Configuration,
    private val vertx: Vertx,
    private val mongoClient: MongoClient
) {

    /**
     * `null` or the Authenticator.kt that uses the Mongo user database to store and retrieve credentials.
     */
    val authProvider: MongoAuthentication
        get() {
            val authProperties = MongoAuthenticationOptions()
            return MongoAuthentication.create(mongoClient, authProperties)
        }

    /**
     * The auth provider to be used for authentication.
     */
    val jwtAuthProvider: JWTAuth
        get() {
            return JWTAuth.create(
                vertx,
                JWTAuthOptions()
                    .addPubSecKey(
                        PubSecKeyOptions()
                            .setAlgorithm(Authenticator.JWT_HASH_ALGORITHM)
                            .setBuffer(publicKey)
                    )
                    .addPubSecKey(
                        PubSecKeyOptions()
                            .setAlgorithm(Authenticator.JWT_HASH_ALGORITHM)
                            .setBuffer(privateKey)
                    )
            )
        }

    /**
     * The issuer to be used for authentication.
     */
    val issuer: String = "$host$endpoint"

    /**
     * The audience to be used for authentication.
     */
    val audience: String = "$host$endpoint"

    /**
     * A parameter telling the system how long a new JWT token stays valid in seconds.
     */
    val tokenExpirationTime: Int
        get() {
            val tokenExpirationTimeValue = configuration.jwtExpiration
            Validate.isTrue(tokenExpirationTimeValue > 0)
            return tokenExpirationTimeValue
        }

    private val host: String
        get() = configuration.serviceHttpAddress.host

    private val endpoint: String
        get() = configuration.serviceHttpAddress.path

    private val publicKey: String
        get() = loadKey(configuration.jwtPublic)

    private val privateKey: String
        get() = loadKey(configuration.jwtPrivate)

    private fun loadKey(path: Path): String {
        if (path.exists()) {
            return Files.readString(path)
        } else {
            throw InvalidConfig("Key file for JWT authentication missing at $path")
        }
    }
}
