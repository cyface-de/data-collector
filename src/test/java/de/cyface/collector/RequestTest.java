/*
 * Copyright 2018 Cyface GmbH
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

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.collector.handler.DefaultHandler;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * This tests the REST-API provided by the collector and used to upload the data
 * to the server.
 * 
 * @author Klemens Muthmann
 * @since 1.0.0
 * @version 2.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class RequestTest extends MongoTest {
	/**
	 * The test <code>Vertx</code> instance.
	 */
	private Vertx vertx;
	/**
	 * A Mongo database lifecycle handler. This provides the test with the
	 * capabilities to run and shutdown a Mongo database for testing purposes.
	 */
	private MongoTest mongoTest;
	/**
	 * The port running the test API under.
	 */
	private int port;
	/**
	 * A <code>WebClient</code> to access the test API.
	 */
	private WebClient client;
	

	/**
	 * Deploys the {@link MainVerticle} in a test context.
	 * 
	 * @param context The test context used to control the test <code>Vertx</code>.
	 * @throws IOException Fails the test if anything unexpected goes wrong.
	 */
	@Before
	public void deployVerticle(final TestContext context) throws IOException {
		
		mongoTest = new MongoTest();
		mongoTest.setUpMongoDatabase();

		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();

		JsonObject mongoDbConfig = new JsonObject()
				.put("connection_string", "mongodb://localhost:" + MongoTest.MONGO_PORT).put("db_name", "cyface");

		JsonObject config = new JsonObject().put(Parameter.MONGO_DATA_DB.key(), mongoDbConfig)
				.put(Parameter.MONGO_USER_DB.key(), mongoDbConfig).put(Parameter.HTTP_PORT.key(), port);
		DeploymentOptions options = new DeploymentOptions().setConfig(config);

		vertx = Vertx.vertx();
		vertx.deployVerticle(MainVerticle.class.getName(), options, context.asyncAssertSuccess());

		String truststorePath = this.getClass().getResource("/localhost.jks").getFile();

		client = WebClient.create(vertx, new WebClientOptions().setSsl(true)
				.setTrustStoreOptions(new JksOptions().setPath(truststorePath).setPassword("secret")));
	}

	/**
	 * Stops the test <code>Vertx</code> instance.
	 * 
	 * @param context The test context for running <code>Vertx</code> under test.
	 */
	@After
	public void stopVertx(final TestContext context) {
		vertx.close(context.asyncAssertSuccess());
		mongoTest.stopMongoDb();
	}

	/**
	 * Tests the correct workings of the general functionality using the
	 * {@link DefaultHandler}.
	 * 
	 * @param context The test context for running <code>Vertx</code> under test.
	 * @throws Throwable Fails the test if anything unexpected happens.
	 */
	@Test
	public void testDefaultHandler(final TestContext context) throws Throwable {
		final Async async = context.async();

		TestUtils.authenticate(client, authResponse -> {
			if (authResponse.succeeded()) {
				context.assertEquals(authResponse.result().statusCode(), 200);
				final String token = authResponse.result().bodyAsString();

				client.get(port, "localhost", "/api/v2").putHeader("Authorization", "Bearer " + token).ssl(true)
						.send(response -> {
							if (response.succeeded()) {
								context.assertEquals(response.result().statusCode(), 200);
								final String body = response.result().bodyAsString();
								context.assertTrue(body.contains("Cyface"));
							} else {
								context.fail();
							}
							async.complete();
						});
			} else {
				context.fail("Unable to authenticate");
			}
		}, port);

		async.await(3000L);

	}
}
