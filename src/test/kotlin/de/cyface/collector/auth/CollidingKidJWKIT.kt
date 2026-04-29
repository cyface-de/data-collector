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
import com.natpryce.hamkrest.equalTo
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test

/**
 * Documents what Vert.x does when two JWKs share the same `kid` value and are both loaded
 * into a single [io.vertx.ext.auth.jwt.JWTAuth] instance via [MultiJWKAuthHandlerBuilder].
 *
 * This scenario no longer occurs in production now that the partner's OAuth server issues JWKs
 * with unique `kid` values. The test is kept to record the observed Vert.x behaviour so that
 * future maintainers understand what happens if duplicate kids are ever encountered again.
 *
 * Observed behaviour: Vert.x stores keys in a map keyed by `kid`. When two keys share the same
 * `kid`, the last key in the list overwrites the first. Only tokens signed by the last key with
 * the colliding `kid` are accepted; tokens signed by earlier keys with the same `kid` are
 * rejected, because Vert.x resolves the kid to the last-registered public key.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
class CollidingKidJWKIT {

    @Test
    fun `when two JWKs share the same kid only the last-registered key accepts tokens`(vertx: Vertx) = runTest {
        val keyGen = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }
        val keyPair1 = keyGen.generateKeyPair()
        val keyPair2 = keyGen.generateKeyPair()

        // Both JWKs intentionally share the same kid to document the collision behaviour.
        val sharedKid = "shared-kid"
        val token1 = generateToken(keyPair1, sharedKid)
        val token2 = generateToken(keyPair2, sharedKid)

        val jwk1 = toJwk(keyPair1.public as RSAPublicKey, sharedKid)
        val jwk2 = toJwk(keyPair2.public as RSAPublicKey, sharedKid)

        val router = Router.router(vertx)
        // jwk1 is registered first, jwk2 second — jwk2 overwrites jwk1 for "shared-kid".
        val authHandler = MultiJWKAuthHandlerBuilder(vertx, listOf(jwk1, jwk2)).create(router)
        router.get("/test").handler(authHandler).handler { ctx -> ctx.response().setStatusCode(200).end() }
        val port = vertx.createHttpServer().requestHandler(router).listen(0).coAwait().actualPort()

        val client = WebClient.create(vertx)

        // token2 is signed by keyPair2, whose public key is stored as "shared-kid" → accepted.
        val response2 = client.get(port, "localhost", "/test")
            .putHeader("Authorization", "Bearer $token2").send().coAwait()
        assertThat("token from last-registered key should be accepted", response2.statusCode(), equalTo(200))

        // token1 is signed by keyPair1, but "shared-kid" now maps to keyPair2's public key → rejected.
        val response1 = client.get(port, "localhost", "/test")
            .putHeader("Authorization", "Bearer $token1").send().coAwait()
        assertThat("token from overwritten key should be rejected", response1.statusCode(), equalTo(401))
    }

    private fun generateToken(keyPair: KeyPair, kid: String): String {
        val header = base64Url("""{"alg":"RS256","typ":"JWT","kid":"$kid"}""")
        val payload = base64Url("""{"sub":"test-user","iat":${System.currentTimeMillis() / 1000}}""")
        val signingInput = "$header.$payload"

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(signingInput.toByteArray(Charsets.US_ASCII))
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        return "$signingInput.$sig"
    }

    private fun toJwk(key: RSAPublicKey, kid: String): JsonObject =
        JsonObject()
            .put("kty", "RSA")
            .put("alg", "RS256")
            .put("use", "sig")
            .put("kid", kid)
            .put("n", encodeBase64Url(key.modulus))
            .put("e", encodeBase64Url(key.publicExponent))

    private fun base64Url(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

    private fun encodeBase64Url(value: BigInteger): String {
        var bytes = value.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
