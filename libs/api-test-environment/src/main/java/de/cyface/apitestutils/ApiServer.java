/*
 * Copyright 2019-2022 Cyface GmbH
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
package de.cyface.apitestutils;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.commons.lang3.Validate;

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
 * @version 2.1.0
 * @since 1.0.0
 */
public final class ApiServer {

    /**
     * The host to run the test {@code ApiVerticle}
     */
    public static final String HTTP_HOST = "localhost";
    /**
     * The port the server is reachable at.
     */
    private int port;
    /**
     * The endpoint on which the test {@code ApiVerticle} listens to.
     */
    private final String httpEndpoint;
    /**
     * The parameter key required to be passed to the {@code Config} of the test {@code ApiVerticle}.
     */
    private final String httpEndpointParameterKey;

    public ApiServer(final String httpEndpointParameterKey, final String httpEndpoint) {
        this.httpEndpointParameterKey = httpEndpointParameterKey;
        this.httpEndpoint = httpEndpoint;
    }

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
    @SuppressWarnings("unused") // Part of the API
    public void start(final Vertx vertx, final VertxTestContext testContext, final TestMongoDatabase mongoDatabase,
            final String verticleClassName, final Handler<AsyncResult<WebClient>> resultHandler) throws IOException {
        start(vertx, testContext, mongoDatabase, verticleClassName, new JsonObject(), resultHandler);
    }

    /**
     * Starts a test Cyface Data server and creates a Vert.x <code>WebClient</code> usable to access the API.
     *
     * @param vertx The <code>Vertx</code> instance to start and access the server
     * @param testContext The <code>TestContext</code> to create a new Server and <code>WebClient</code>
     * @param mongoDatabase The Mongo database to store the test data to
     * @param resultHandler The handler called after this server has finished starting
     * @param verticleClassName The name of the {@code ApiVerticle} to deploy
     * @param config A {@code JsonObject} which contains custom config parameters to be used when deploying the verticle
     * @throws IOException If the server port could not be opened
     */
    public void start(final Vertx vertx, final VertxTestContext testContext, final TestMongoDatabase mongoDatabase,
            final String verticleClassName, final JsonObject config,
            final Handler<AsyncResult<WebClient>> resultHandler) throws IOException {

        final ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        final var privateTestKey = this.getClass().getResource("/private_key.pem");
        final var publicTestKey = this.getClass().getResource("/public.pem");
        config.put(Parameter.MONGO_DATA_DB.key(), mongoDatabase.config())
                .put(Parameter.MONGO_USER_DB.key(), mongoDatabase.config())
                .put(Parameter.HTTP_PORT.key(), port)
                .put(Parameter.JWT_PRIVATE_KEY_FILE_PATH.key(), Validate.notNull(privateTestKey).getFile())
                .put(Parameter.JWT_PUBLIC_KEY_FILE_PATH.key(), Validate.notNull(publicTestKey).getFile())
                .put(Parameter.HTTP_HOST.key(), HTTP_HOST)
                .put(httpEndpointParameterKey, httpEndpoint);
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

        client.post(port(), HTTP_HOST, httpEndpoint + "login").sendJsonObject(body, handler);
    }

    /**
     * Send an authenticated {@code GET} request to a test server instance
     *
     * @param client The Vert.x <code>WebClient</code> to use
     * @param endpoint The service endpoint to call to get some data
     * @param testContext The <code>VertxTextContext</code> provided by the current test case
     * @param format A field to identify the requested format, such as 'csv' or 'json'.
     * @param resultHandler A handler provided with the result of the get request
     */
    @SuppressWarnings("unused") // Part of the API
    public void get(final WebClient client, final String endpoint, final VertxTestContext testContext,
            final String format, final Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler) {
        authenticate(client, testContext.succeeding(response -> {

            final String authToken = response.getHeader("Authorization");

            if (response.statusCode() == 200 && authToken != null) {
                final HttpRequest<Buffer> builder = client.get(port(), HTTP_HOST, httpEndpoint + endpoint);
                builder.putHeader("Authorization", "Bearer " + authToken);
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
     * Send an unauthenticated {@code POST} request to a test server instance
     *
     * @param client The Vert.x <code>WebClient</code> to use
     * @param endpoint The service endpoint to call to get some data
     * @param body The body to post
     * @param testContext The <code>VertxTextContext</code> provided by the current test case
     * @param resultHandler A handler provided with the result of the get request
     */
    @SuppressWarnings("unused") // Part of the API
    public void postUnauthorized(final WebClient client, final String endpoint, final JsonObject body,
            final VertxTestContext testContext, final Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler) {

        client.post(port(), HTTP_HOST, httpEndpoint + endpoint)
                .sendJsonObject(body, resultHandler);
    }

    /**
     * Send an unauthenticated {@code GET} request to a test server instance
     *
     * @param client The Vert.x <code>WebClient</code> to use
     * @param endpoint The service endpoint to call to get some data
     * @param testContext The <code>VertxTextContext</code> provided by the current test case
     * @param resultHandler A handler provided with the result of the get request
     */
    @SuppressWarnings("unused") // Part of the API
    public void getUnauthorized(final WebClient client, final String endpoint, final VertxTestContext testContext,
            final Handler<AsyncResult<HttpResponse<Buffer>>> resultHandler) {

        client.get(port(), HTTP_HOST, httpEndpoint + endpoint)
                .send(resultHandler);
    }

    /**
     * @return The port the server is reachable at.
     */
    private int port() {
        return port;
    }
}
