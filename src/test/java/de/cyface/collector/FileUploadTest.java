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
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.multipart.MultipartForm;

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class FileUploadTest {

	private final static Logger LOGGER = LoggerFactory.getLogger(FileUploadTest.class);
	/**
	 * The test <code>Vertx</code> instance.
	 */
	private Vertx vertx;
	/**
	 * The port running the test API under.
	 */
	private int port;
	/**
	 * A <code>WebClient</code> to access the test API.
	 */
	private WebClient client;
	private String deviceIdentifier = UUID.randomUUID().toString();
	private String measurementIdentifier = String.valueOf(1L);

	private MultipartForm form = MultipartForm.create();
	/**
	 * The test Mongo database.
	 */
	private static MongodProcess mongo;
	private static MongodExecutable mongodExecutable;

	/**
	 * Sets up the Mongo database used for the test instance.
	 * 
	 * @throws IOException Fails the test if anything unexpected goes wrong.
	 */
	@BeforeClass
	public static void setUpMongoDatabase() throws IOException {
		LOGGER.info("Begin setUpMongoDatabase");
		MongodStarter starter = MongodStarter.getDefaultInstance();
		IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION)
				.net(new Net("localhost", TestUtils.MONGO_PORT, Network.localhostIsIPv6())).build();
		mongodExecutable = starter.prepare(mongodConfig);
		mongo = mongodExecutable.start();
		LOGGER.info("End setUpMongoDatabase with {}", mongo);
	}

	/**
	 * Deploys the {@link MainVerticle} in a test context.
	 * 
	 * @param context The test context used to control the test <code>Vertx</code>.
	 * @throws IOException Fails the test if anything unexpected goes wrong.
	 */
	private void deployVerticle(final TestContext context) throws IOException {
		LOGGER.info("Begin deployVerticle");

		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();

		JsonObject mongoDbConfig = new JsonObject()
				.put("connection_string", "mongodb://localhost:" + TestUtils.MONGO_PORT).put("db_name", "cyface");

		JsonObject config = new JsonObject().put(Parameter.MONGO_DATA_DB.key(), mongoDbConfig)
				.put(Parameter.MONGO_USER_DB.key(), mongoDbConfig).put(Parameter.HTTP_PORT.key(), port);
		DeploymentOptions options = new DeploymentOptions().setConfig(config);

		vertx = Vertx.vertx();
		vertx.deployVerticle(MainVerticle.class.getName(), options, context.asyncAssertSuccess());

		String truststorePath = this.getClass().getResource("/localhost.jks").getFile();

		client = WebClient.create(vertx, new WebClientOptions().setSsl(true)
				.setTrustStoreOptions(new JksOptions().setPath(truststorePath).setPassword("secret")));
		LOGGER.info("End deployVerticle");
	}

	@Before
	public void setUp(final TestContext context) throws IOException {
		LOGGER.info("Begin setUp");
		deployVerticle(context);

		this.deviceIdentifier = UUID.randomUUID().toString();
		this.measurementIdentifier = String.valueOf(1L);

		this.form = MultipartForm.create();
		form.attribute("deviceId", deviceIdentifier);
		form.attribute("measurementId", measurementIdentifier);
		form.attribute("deviceType", "HTC Desire");
		form.attribute("osVersion", "4.4.4");

		LOGGER.info("End setUp");
	}

	/**
	 * Stops the test <code>Vertx</code> instance.
	 * 
	 * @param context The test context for running <code>Vertx</code> under test.
	 */
	@After
	public void stopVertx(final TestContext context) {
		LOGGER.info("Stopping vertx. Running mongo is {}", mongo);
		vertx.close(context.asyncAssertSuccess());
	}

	/**
	 * Stops the test Mongo database after all tests have been finished.
	 */
	@AfterClass
	public static void stopMongoDb() {
		LOGGER.info("Stopping mongo db {}", mongo);
		mongo.stop();
		mongodExecutable.stop();
	}

	/**
	 * Tests uploading a file to the Vertx API.
	 * 
	 * @param context The test context for running <code>Vertx</code> under test.
	 * @throws Exception Fails the test if anything unexpected happens.
	 */
	@Test
	public void testPostFile(final TestContext context) throws Exception {
		LOGGER.info("Begin testPostFile");
		uploadAndCheckForSuccess(context, "/test.bin");
		LOGGER.info("End testPostFile");
	}

	/**
	 * Tests that uploading a larger file works as expected.
	 * 
	 * @param context The test context for running <code>Vertx</code> under test.
	 */
	@Test
	public void testPostLargeFile(final TestContext context) {
		LOGGER.info("Begin testPostLargeFile");
		uploadAndCheckForSuccess(context, "/iphone-neu.ccyf");
		LOGGER.info("End testPostLargeFile");
	}

	private void uploadAndCheckForSuccess(final TestContext context, final String testFileResourceName) {
		Async async = context.async();
		final URL testFileResource = this.getClass().getResource(testFileResourceName);

		form.binaryFileUpload("fileToUpload",
				String.format(Locale.US, "%s_%s.cyf", deviceIdentifier, measurementIdentifier),
				testFileResource.getFile(), "application/octet-stream");

		EventBus eventBus = vertx.eventBus();
		eventBus.consumer(EventBusAddresses.MEASUREMENT_SAVED, message -> {
			LOGGER.debug("I have received a message: " + message.body());
			async.complete();
		});
		eventBus.consumer(EventBusAddresses.SAVING_MEASUREMENT_FAILED, message -> {
			context.fail("Unable to save measurement " + message.body());
		});

		TestUtils.authenticate(client, authResponse -> {
			if (authResponse.succeeded()) {
				context.assertEquals(authResponse.result().statusCode(), 200);
				final String authToken = authResponse.result().bodyAsString();

				final HttpRequest<Buffer> builder = client.post(port, "localhost", "/api/v2/measurements").ssl(true);
				builder.putHeader("Authorization", "Bearer " + authToken);
				builder.sendMultipartForm(form, ar -> {
					if (ar.succeeded()) {
						context.assertEquals(ar.result().statusCode(), 201);
						context.assertTrue(ar.succeeded());
						context.assertNull(ar.cause());
//						async.complete();
					} else {
						context.fail(ar.cause());
					}
				});
			} else {
				context.fail(authResponse.cause());
			}
		}, port);

		async.await(3000L);
	}
}
