package de.cyface.collector;

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
        final var authoriser = JWTAuth.create(vertx, options);
        final var body = new JsonObject().put("username", "test").put("password", "test-password").put("aud", issuer)
                .put("iss", issuer);
        final var token = authoriser.generateToken(body);
        System.out.println(token);
        final var authenticationFuture = authoriser.authenticate(new TokenCredentials(token));
        authenticationFuture.onComplete(ctx.succeeding(user -> {
            System.out.println(user);
            ctx.completeNow();
        }));
    }
}
