/*
 * Copyright 2025 Cyface GmbH
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
 * Integration test verifying that [MultiJWKAuthHandlerBuilder] accepts tokens from two identity
 * providers whose JWKs share the same `kid` value.
 *
 * RSA key pairs and JWTs are generated entirely at runtime using the JDK so that no real keys or
 * tokens need to be committed to the repository.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
class MultiJWKAuthIT {

    @Test
    fun `tokens from two IdPs with colliding kid are both accepted`(vertx: Vertx) = runTest {
        val keyGen = KeyPairGenerator.getInstance("RSA").also { it.initialize(2048) }
        val keyPair1 = keyGen.generateKeyPair()
        val keyPair2 = keyGen.generateKeyPair()

        val token1 = generateToken(keyPair1)
        val token2 = generateToken(keyPair2)

        // Both JWKs intentionally share the same kid to reproduce the collision scenario.
        val jwk1 = toJwk(keyPair1.public as RSAPublicKey)
        val jwk2 = toJwk(keyPair2.public as RSAPublicKey)

        val router = Router.router(vertx)
        val authHandler = MultiJWKAuthHandlerBuilder(vertx, listOf(jwk1, jwk2)).create(router)
        router.get("/test").handler(authHandler).handler { ctx -> ctx.response().setStatusCode(200).end() }
        val port = vertx.createHttpServer().requestHandler(router).listen(0).coAwait().actualPort()

        val client = WebClient.create(vertx)

        val response1 = client.get(port, "localhost", "/test")
            .putHeader("Authorization", "Bearer $token1").send().coAwait()
        assertThat("token from IdP 1 should be accepted", response1.statusCode(), equalTo(200))

        val response2 = client.get(port, "localhost", "/test")
            .putHeader("Authorization", "Bearer $token2").send().coAwait()
        assertThat("token from IdP 2 should be accepted", response2.statusCode(), equalTo(200))

        val response3 = client.get(port, "localhost", "/test")
            .putHeader("Authorization", "Bearer not.a.valid.jwt").send().coAwait()
        assertThat("invalid token should be rejected", response3.statusCode(), equalTo(401))
    }

    /**
     * Builds a minimal RS256 JWT signed with the given key pair's private key.
     * Uses only JDK APIs to avoid any dependency on Vert.x's internal token generation.
     */
    private fun generateToken(keyPair: KeyPair): String {
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64Url("""{"sub":"test-user","iat":${System.currentTimeMillis() / 1000}}""")
        val signingInput = "$header.$payload"

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(signingInput.toByteArray(Charsets.US_ASCII))
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        return "$signingInput.$sig"
    }

    private fun toJwk(key: RSAPublicKey, kid: String = "collision-kid"): JsonObject =
        JsonObject()
            .put("kty", "RSA")
            .put("alg", "RS256")
            .put("use", "sig")
            .put("kid", kid)
            .put("n", encodeBase64Url(key.modulus))
            .put("e", encodeBase64Url(key.publicExponent))

    private fun base64Url(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

    /**
     * Encodes a [BigInteger] as a Base64url string without padding, stripping the leading zero byte
     * that [BigInteger.toByteArray] may add for the two's-complement sign bit.
     */
    private fun encodeBase64Url(value: BigInteger): String {
        var bytes = value.toByteArray()
        if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
