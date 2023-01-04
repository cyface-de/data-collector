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
 * @property configuration The global configuration to load all the configuration for authentication from.
 * @property vertx The vertx instance used by this application.
 * @property mongoClient A Vertx [MongoClient] used to access the authentication database.
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

    /**
     * The host of the data collector service.
     */
    private val host: String
        get() = configuration.serviceHttpAddress.host

    /**
     * The endpoint hosting the data collector service.
     */
    private val endpoint: String
        get() = configuration.serviceHttpAddress.path

    /**
     * The public key used for JWT authentication.
     */
    private val publicKey: String
        get() = loadKey(configuration.jwtPublic)

    /**
     * The private key used for JWT authentication.
     */
    private val privateKey: String
        get() = loadKey(configuration.jwtPrivate)

    /**
     * Loads a key file into memory.
     *
     * @param path The local file system path to the key file to load.
     */
    private fun loadKey(path: Path): String {
        if (path.exists()) {
            return Files.readString(path)
        } else {
            throw InvalidConfig("Key file for JWT authentication missing at $path")
        }
    }
}
