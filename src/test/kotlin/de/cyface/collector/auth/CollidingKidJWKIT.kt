/*
 * Copyright 2026 Cyface GmbH
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
package de.cyface.collector.auth

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import de.cyface.collector.configuration.InvalidConfig
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test

/**
 * Verifies that [MultiJWKAuthHandlerBuilder] enforces the "unique kid" invariant by failing fast
 * when two JWKs share the same `kid` value, or when a JWK is missing a `kid` altogether.
 *
 * Previously Vert.x silently let the last key with a colliding `kid` overwrite the first, so tokens
 * signed by the earlier key were rejected at request time. That produced intermittent, hard-to-debug
 * 401s. The builder now rejects such configurations at construction time (i.e. at application
 * startup), so the problem can never reach production traffic.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
class CollidingKidJWKIT {

    @Test
    fun `two JWKs sharing the same kid are rejected at construction`(vertx: Vertx) {
        val keyGen = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }
        val keyPair1 = keyGen.generateKeyPair()
        val keyPair2 = keyGen.generateKeyPair()

        val sharedKid = "shared-kid"
        val jwk1 = toJwk(keyPair1.public as RSAPublicKey, sharedKid)
        val jwk2 = toJwk(keyPair2.public as RSAPublicKey, sharedKid)

        val exception = assertThrows<InvalidConfig> {
            MultiJWKAuthHandlerBuilder(vertx, listOf(jwk1, jwk2))
        }
        assertThat(exception.message, containsSubstring("unique 'kid'"))
        assertThat(exception.message, containsSubstring(sharedKid))
    }

    @Test
    fun `a JWK without a kid is rejected at construction`(vertx: Vertx) {
        val keyGen = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }
        val withKid = toJwk(keyGen.generateKeyPair().public as RSAPublicKey, "kid-1")
        val withoutKid = toJwk(keyGen.generateKeyPair().public as RSAPublicKey, null)

        val exception = assertThrows<InvalidConfig> {
            MultiJWKAuthHandlerBuilder(vertx, listOf(withKid, withoutKid))
        }
        assertThat(exception.message, containsSubstring("non-blank 'kid'"))
    }

    @Test
    fun `an empty JWK list is rejected at construction`(vertx: Vertx) {
        val exception = assertThrows<InvalidConfig> {
            MultiJWKAuthHandlerBuilder(vertx, emptyList())
        }
        assertThat(exception.message, containsSubstring("at least one JWK"))
    }

    private fun toJwk(key: RSAPublicKey, kid: String?): JsonObject =
        JsonObject()
            .put("kty", "RSA")
            .put("alg", "RS256")
            .put("use", "sig")
            .apply { if (kid != null) put("kid", kid) }
            .put("n", encodeBase64Url(key.modulus))
            .put("e", encodeBase64Url(key.publicExponent))

    private fun encodeBase64Url(value: BigInteger): String {
        var bytes = value.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
