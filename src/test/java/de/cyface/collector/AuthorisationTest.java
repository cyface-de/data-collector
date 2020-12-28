package de.cyface.collector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

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
 */
@ExtendWith(VertxExtension.class)
public class AuthorisationTest {
    @Test
    void test(final Vertx vertx, final VertxTestContext ctx) throws URISyntaxException, IOException {
        final var publicKey = Files.readString(Path.of(this.getClass().getResource("/public.pem").toURI()));
        final var privateKey = Files.readString(Path.of(this.getClass().getResource("/private_key.pem").toURI()));
        final var keyOptions = new PubSecKeyOptions().setAlgorithm("HS256").setBuffer(publicKey)
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
            ctx.verify(() -> {
                assertThat(user.principal().getString("username"), is(equalTo("test")));
                assertThat(user.principal().getString("password"), is(equalTo("test-password")));
                assertThat(user.principal().getString("aud"), is(equalTo(issuer)));
                assertThat(user.principal().getString("iss"), is(equalTo(issuer)));
            });
            ctx.completeNow();
        }));
    }
}
