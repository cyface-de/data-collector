/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;

import de.cyface.api.Parameter;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;

/**
 * A class providing capabilities for tests to communicate with a Cyface Data server.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public final class ApiServer {
    /**
     * The endpoint on which the test {@code ApiVerticle} listens to.
     */
    public static final String HTTP_ENDPOINT = "/api/v2/";
    /**
     * The host to run the test {@code ApiVerticle}
     */
    public static final String HTTP_HOST = "localhost";
    /**
     * The port the server is reachable at.
     */
    private int port;

    /**
     * Starts a test Cyface Data server and creates a Vert.x <code>WebClient</code> usable to access the API.
     *
     * @param vertx The <code>Vertx</code> instance to start and access the server
     * @param testContext The <code>TestContext</code> to create a new Server and <code>WebClient</code>
     * @param mongoDatabase The Mongo database to store the test data to
     * @param resultHandler The handler called after this server has finished starting
     * @param verticleClassName The name of the {@code ApiVerticle} to deploy
     * @throws IOException If the server port could not be opened
     */
    public void start(final Vertx vertx, final VertxTestContext testContext, final TestMongoDatabase mongoDatabase,
            final String verticleClassName, final Handler<AsyncResult<WebClient>> resultHandler) throws IOException {

        final ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        final URL privateTestKey = this.getClass().getResource("/private_key.pem");
        final JsonObject config = new JsonObject().put(Parameter.MONGO_DATA_DB.key(), mongoDatabase.config())
                .put(Parameter.MONGO_USER_DB.key(), mongoDatabase.config())
                .put(Parameter.HTTP_PORT.key(), port)
                .put(Parameter.JWT_PRIVATE_KEY_FILE_PATH.key(), privateTestKey.getFile())
                .put(Parameter.JWT_PUBLIC_KEY_FILE_PATH.key(), this.getClass().getResource("/public.pem").getFile())
                .put(Parameter.HTTP_HOST.key(), HTTP_HOST)
                .put(Parameter.HTTP_ENDPOINT.key(), HTTP_ENDPOINT);
        final DeploymentOptions options = new DeploymentOptions().setConfig(config);

        vertx.deployVerticle(verticleClassName, options, testContext
                .succeeding(result -> resultHandler.handle(Future.succeededFuture(WebClient.create(vertx)))));
    }

    /**
     * Authenticates with the exporter server providing the received authentication token.
     *
     * @param client The client to use to access the server
     * @param handler <code>Handler</code> called when the response has returned
     */
    public void authenticate(final WebClient client, final Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        final JsonObject body = new JsonObject();
        body.put("username", "admin");
        body.put("password", "secret");

        client.post(port(), HTTP_HOST, HTTP_ENDPOINT + "login").sendJsonObject(body, handler);
    }

    /**
     * Send a test get request to a test server instance
     *
     * @param client The Vert.x <code>WebClient</code> to use
     * @param endpoint The service endpoint to call to get some data
     * @param testContext The <code>VertxTextContext</code> provided by the current test case
     * @param groupName The user group to export data for
     * @param format A field to identify the requested format, such as 'csv' or 'json'.
     * @param resultHandler A handler provided with the result of the get request
     */
    public void get(final WebClient client, final String endpoint, final VertxTestContext testContext,
            final String groupName, final String format, final Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler) {
        authenticate(client, testContext.succeeding(response -> {

            final String authToken = response.getHeader("Authorization");

            if (response.statusCode() == 200 && authToken != null) {
                final HttpRequest<Buffer> builder = client.get(port(), HTTP_HOST, HTTP_ENDPOINT + endpoint);
                builder.putHeader("Authorization", "Bearer " + authToken);
                builder.putHeader("group", groupName);
                builder.putHeader("format", format);
                builder.send(resultHandler);
            } else {
                testContext.failNow(new IllegalStateException(String.format(
                        "Unable to authenticate. Authentication request response status was %d with authentication token %s.",
                        response.statusCode(), authToken)));
            }
        }));
    }

    /**
     * @return The port the server is reachable at.
     */
    private int port() {
        return port;
    }
}
