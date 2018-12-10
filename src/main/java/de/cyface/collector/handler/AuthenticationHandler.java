package de.cyface.collector.handler;

import org.apache.commons.lang3.Validate;

import de.cyface.collector.MainVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles authentication request on the Cyface Collector API. This is implemented with JSON Web Token (JWT). To get a
 * valid token, the user is required to authenticate using username and password, which issues a new token. This token
 * can then be used to transmit the actual request.
 * 
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
public class AuthenticationHandler implements Handler<RoutingContext> {

    /**
     * The logger used by objects of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(AuthenticationHandler.class);

    private final MongoAuth authProvider;
    private final JWTAuth jwtAuthProvider;

    public AuthenticationHandler(final MongoAuth authProvider, JWTAuth jwtAuthProvider) {
        Validate.notNull(authProvider);
        Validate.notNull(jwtAuthProvider);

        this.authProvider = authProvider;
        this.jwtAuthProvider = jwtAuthProvider;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            JsonObject body = ctx.getBodyAsJson();
            LOGGER.info("Receiving authentication request: " + body);
            authProvider.authenticate(body, r -> {
                if (r.succeeded()) {
                    LOGGER.info("Authentication successful!");
                    LOGGER.info(body);
                    String generatedToken = jwtAuthProvider.generateToken(body,
                            new JWTOptions().setExpiresInSeconds(60).setAlgorithm(MainVerticle.JWT_HASH_ALGORITHM));
                    LOGGER.info("New JWT Token: " + generatedToken);
                    // Returning the token as response body because the RequestTest fails to read the header
                    // Returning the token as response header because Android fails to read the response body
                    ctx.response().putHeader("Authorization", generatedToken).setStatusCode(200).end(generatedToken);
                } else {
                    ctx.fail(401);
                }
            });
        } catch (DecodeException e) {
            LOGGER.error("Unable to decode body!", e);
            ctx.fail(401);
        }
    }

}
