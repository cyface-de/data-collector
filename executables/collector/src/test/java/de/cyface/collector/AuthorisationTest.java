/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.collector;

import static io.vertx.ext.auth.impl.jose.JWS.RS256;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test whether user authorisation works as expected.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
@ExtendWith(VertxExtension.class)
public class AuthorisationTest {
    /**
     * Happy Path integration test for user authorisation.
     *
     * @param vertx A test Vert.x instance
     * @param ctx The Vert.x test context
     * @throws URISyntaxException If test key pair could not be loaded
     * @throws IOException If test key pair could not be read
     */
    @Test
    @DisplayName("Test that authenticating a user works via the happy path")
    void test(final Vertx vertx, final VertxTestContext ctx) throws URISyntaxException, IOException {
        final var publicKey = Files.readString(Path.of(this.getClass().getResource("/public.pem").toURI()));
        final var privateKey = Files.readString(Path.of(this.getClass().getResource("/private_key.pem").toURI()));
        final var keyOptions = new PubSecKeyOptions().setAlgorithm(RS256).setBuffer(publicKey)
                .setBuffer(privateKey);
        final var options = new JWTAuthOptions();
        final var issuer = "localhost:8080/api/v2/";

        options.addPubSecKey(keyOptions)
                .setJWTOptions(new JWTOptions().setIssuer(issuer).setAudience(Collections.singletonList(issuer)));
        final var authorizer = JWTAuth.create(vertx, options);
        final var body = new JsonObject().put("username", "test").put("password", "test-password").put("aud", issuer)
                .put("iss", issuer);
        final var token = authorizer.generateToken(body);
        final var authenticationFuture = authorizer.authenticate(new TokenCredentials(token));
        authenticationFuture.onComplete(ctx.succeeding(user -> {
            final var expectedPrincipal = new JsonObject().put("access_token", token).put("username", "test")
                    .put("password", "test-password")
                    .put("aud", issuer).put("iss", issuer);
            ctx.verify(() -> {
                assertThat("User prinicpal did not match expected user!", user.principal(),
                        is(equalTo(expectedPrincipal)));
            });
            ctx.completeNow();
        }));
    }
}
