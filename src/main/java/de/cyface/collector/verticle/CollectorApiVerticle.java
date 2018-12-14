/*
 * Copyright 2018 Cyface GmbH
 * This file is part of the Cyface Data Collector.
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.verticle;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.commons.lang3.Validate;

import de.cyface.collector.Parameter;
import de.cyface.collector.Utils;
import de.cyface.collector.handler.AuthenticationHandler;
import de.cyface.collector.handler.FailureHandler;
import de.cyface.collector.handler.MeasurementHandler;
import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all other Verticles and configures the
 * endpoints used to provide the REST-API.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.0
 * @since 2.0.0
 */
public final class CollectorApiVerticle extends AbstractVerticle {

    /**
     * The <code>Logger</code> used for objects of this class. Configure it by changing the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectorApiVerticle.class);

    /**
     * The hashing algorithm used for public and private keys to generate and check JWT tokens.
     */
    public static final String JWT_HASH_ALGORITHM = "RS256";

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        Validate.notNull(startFuture);

        prepareEventBus();

        JsonObject mongoDatabaseConfiguration = Parameter.MONGO_DATA_DB.jsonValue(vertx);
        deployVerticles(mongoDatabaseConfiguration);

        final String publicKey = extractKey(Parameter.JWT_PUBLIC_KEY_FILE_PATH);
        Validate.notNull(publicKey,
                "Unable to load public key for JWT authentication. Did you provide a valid PEM file using the parameter "
                        + Parameter.JWT_PUBLIC_KEY_FILE_PATH.key() + ".");
        final String privateKey = extractKey(Parameter.JWT_PRIVATE_KEY_FILE_PATH);
        Validate.notNull(privateKey,
                "Unable to load private key for JWT authentication. Did you provide a valid PEM file using the parameter "
                        + Parameter.JWT_PRIVATE_KEY_FILE_PATH.key() + ".");
        final JsonObject mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(vertx);
        final MongoClient client = Utils.createSharedMongoClient(vertx, mongoUserDatabaseConfiguration);
        final MongoAuth authProvider = Utils.buildMongoAuthProvider(client);
        final Router router = setupRoutes(publicKey, privateKey, authProvider);

        final int httpPort = Parameter.COLLECTOR_HTTP_PORT.intValue(vertx, 8080);
        final Future<Void> serverStartFuture = Future.future();
        startHttpServer(router, serverStartFuture, httpPort);

        final String adminUsername = Parameter.ADMIN_USER_NAME.stringValue(vertx, "admin");
        final String adminPassword = Parameter.ADMIN_PASSWORD.stringValue(vertx, "secret");
        final Future<Void> defaultUserCreatedFuture = Future.future();
        client.findOne("user", new JsonObject().put("username", adminUsername), null, result -> {
            if (result.result() == null) {
                authProvider.insertUser(adminUsername, adminPassword, new ArrayList<>(), new ArrayList<>(), ir -> {
                    LOGGER.info("Identifier of new user id: " + ir);
                    defaultUserCreatedFuture.complete();
                });
            } else {
                defaultUserCreatedFuture.complete();
            }
        });
        final CompositeFuture startUpFinishedFuture = CompositeFuture.all(serverStartFuture, defaultUserCreatedFuture);
        startUpFinishedFuture.setHandler(r -> {
            if (r.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(r.cause());
            }
        });
    }

    /**
     * Prepares the Vertx event bus during startup of the application.
     */
    private void prepareEventBus() {
        vertx.eventBus().registerDefaultCodec(Measurement.class, Measurement.getCodec());
    }

    /**
     * Deploys all additional <code>Verticle</code> objects required by the application.
     * 
     * @param mongoDatabaseConfiguration The JSON configuration for the mongo database to store the uploaded data to.
     */
    private void deployVerticles(final JsonObject mongoDatabaseConfiguration) {
        Validate.notNull(mongoDatabaseConfiguration);

        final DeploymentOptions options = new DeploymentOptions().setWorker(true).setConfig(mongoDatabaseConfiguration);
        vertx.deployVerticle(SerializationVerticle.class, options);
    }

    /**
     * Initializes all the routes available via the Cyface Data Collector.
     * 
     * @param publicKey The public key used to check the validity of JWT tokens used for authentication.
     * @param privateKey The private key used to issue new valid JWT tokens.
     * @param mongoAuthProvider Authentication provider used to check for valid user accounts used to generate new JWT
     *            token.
     * @return The Vertx router used by this project.
     */
    private Router setupRoutes(final String publicKey, final String privateKey, final MongoAuth mongoAuthProvider) {

        // Set up authentication check
        PubSecKeyOptions keyOptions = new PubSecKeyOptions().setAlgorithm(JWT_HASH_ALGORITHM).setPublicKey(publicKey)
                .setSecretKey(privateKey);
        final JWTAuthOptions config = new JWTAuthOptions().addPubSecKey(keyOptions);
        final JWTAuth jwtAuthProvider = JWTAuth.create(vertx, config);

        // Routing
        final Router mainRouter = Router.router(vertx);
        final Router v2ApiRouter = Router.router(vertx);

        // Set up default routes
        mainRouter.mountSubRouter("/api/v2", v2ApiRouter);

        // Set up v2 API
//        v2ApiRouter.route().failureHandler(new FailureHandler());
        // v2ApiRouter.route("/").last().handler(new DefaultHandler());
        // Set up authentication route
        v2ApiRouter.route("/login").handler(BodyHandler.create())
                .handler(new AuthenticationHandler(mongoAuthProvider, jwtAuthProvider));
        // Set up data collector route
        v2ApiRouter.post("/measurements").handler(JWTAuthHandler.create(jwtAuthProvider))
                .handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(false)).handler(new MeasurementHandler());

        v2ApiRouter.route("/*").handler(StaticHandler.create("src/main/resources/webroot"));

        mainRouter.route().last().handler(new FailureHandler());

        return mainRouter;
    }

    /**
     * Starts the HTTP server provided by this application. This server runs the Cyface Collector REST-API.
     *
     * @param router The router for all the endpoints the HTTP server should serve.
     * @param startFuture Informs the caller about the successful or failed start of the server.
     * @param httpPort The HTTP port to run the server at.
     */
    private void startHttpServer(final Router router, final Future<Void> startFuture, final int httpPort) {
        vertx.createHttpServer().requestHandler(router).listen(httpPort,
                serverStartup -> completeStartup(serverStartup, startFuture));
    }

    /**
     * Finishes the <code>MainVerticle</code> startup process and informs all interested parties about whether it has
     * been successful or not.
     *
     * @param serverStartup The result of the server startup as provided by <code>Vertx</code>.
     * @param future A future to call to inform all waiting parties about success or failure of the startup process.
     */
    private void completeStartup(final AsyncResult<HttpServer> serverStartup, final Future<Void> future) {
        if (serverStartup.succeeded()) {
            future.complete();
        } else {
            future.fail(serverStartup.cause());
        }
    }

    /**
     * Extracts a key from a PEM keyfile.
     * 
     * @param keyParameter The Vertx configuration parameter specifying the location of the file containing the key.
     * @return The extracted key.
     * @throws FileNotFoundException If the key file was not found.
     * @throws IOException If the key file was not accessible.
     */
    private String extractKey(final Parameter keyParameter) throws FileNotFoundException, IOException {
        final String keyFilePath = keyParameter.stringValue(vertx, null);
        if (keyFilePath == null) {
            return null;
        }

        final StringBuilder keyBuilder = new StringBuilder();
        try (BufferedReader keyFileInput = new BufferedReader(
                new InputStreamReader(new FileInputStream(keyFilePath), "UTF-8"));) {

            String line = keyFileInput.readLine();
            while (line != null) {
                line = keyFileInput.readLine();
                if (line == null || line.startsWith("-----") || line.isEmpty()) {
                    continue;
                } else {
                    keyBuilder.append(line);
                }
            }

        }
        return keyBuilder.toString();
    }
}
