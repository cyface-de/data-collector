/*
 * Copyright 2020-2021 Cyface GmbH
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

import de.cyface.api.ServerConfig;
import de.cyface.apitestutils.fixture.TestFixture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;

/**
 * Test environment containing all dependencies required to run a Cyface data server in a local environment.
 *
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public final class TestEnvironment {
    /**
     * A temporary Mongo database used only for one test.
     */
    private final TestMongoDatabase testMongoDatabase;
    /**
     * The client to be used to access the test Mongo database.
     */
    private MongoClient mongoClient;
    /**
     * A handle to the Cyface exporter server running within this environment.
     */
    private final ApiServer apiServer;
    /**
     * The <code>WebClient</code> to simulate client requests.
     */
    private WebClient webClient;

    /**
     * Create a new object of this class and starting the simulated server.
     * To do anything meaningful with it you need to add some test data via
     * {@link #insertFixture(TestFixture, Handler)}.
     * <b>ATTENTION:</b> Do not forget to call {@link #shutdown()} after you finished using this object, for example in
     * an <code>org.junit.jupiter.api.AfterEach</code> method.
     *
     * @param vertx A <code>Vertx</code> instance to setup the test environment
     * @param testContext The Vertx-JUnit test context used to synchronize the JUnit lifecycle with Vertx
     * @param resultHandler Called after the environment has finished setting up
     * @param verticleClassName The name of the {@code ApiVerticle} to deploy
     * @throws IOException If the temporary Mongo database fails to start
     */
    public TestEnvironment(final Vertx vertx, final VertxTestContext testContext,
            final Handler<AsyncResult<Void>> resultHandler, final String verticleClassName) throws IOException {
        this.testMongoDatabase = new TestMongoDatabase();
        testMongoDatabase.start();

        // Deploy ApiVerticle and a VertX WebClient usable to access the api
        apiServer = new ApiServer();
        apiServer.start(vertx, testContext, testMongoDatabase, verticleClassName, testContext.succeeding(webClient -> {
            this.webClient = webClient;

            // Setup a Mongo client to access the database
            JsonObject mongoDbConfiguration = testMongoDatabase.config();
            this.mongoClient = ServerConfig.createSharedMongoClient(vertx, mongoDbConfiguration);

            resultHandler.handle(Future.succeededFuture());
        }));
    }

    /**
     * Asynchronously inserts the provided {@link TestFixture} into this environment and calls the result handler upon
     * completion.
     *
     * @param fixture The fixture to add to this environment
     * @param resultHandler A handler called after completion
     */
    public void insertFixture(final TestFixture fixture, final Handler<AsyncResult<Void>> resultHandler) {
        fixture.insertTestData(mongoClient, resultHandler);
    }

    /**
     * Call this method after your test has finished to clean up the environment. The most convenient place to call this
     * in an <code>AfterEach</code> method.
     */
    public void shutdown() {
        testMongoDatabase.stop();
    }

    /**
     * @return A handle to the Cyface exporter server running within this environment
     */
    public ApiServer getApiServer() {
        return apiServer;
    }

    /**
     * @return The <code>WebClient</code> to simulate client requests
     */
    public WebClient getWebClient() {
        return webClient;
    }
}
