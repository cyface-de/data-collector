/*
 * Copyright 2018 Cyface GmbH
 * 
 * This file is part of the Cyface Data Collector.
 *
 *  The Cyface Data Collector is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  The Cyface Data Collector is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with the Cyface Data Collector.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector;

import java.io.IOException;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.collector.handler.FormAttributes;
import de.cyface.collector.model.Measurement;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EventBusTest {

	private MongodProcess MONGO;
	private final static int MONGO_PORT = 12345;
	private Vertx vertx;
	private JsonObject mongoConfiguration;

	@Before
	public void initialize() throws IOException {
		MongodStarter starter = MongodStarter.getDefaultInstance();
		IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION)
				.net(new Net(MONGO_PORT, Network.localhostIsIPv6())).build();
		MongodExecutable mongodExecutable = starter.prepare(mongodConfig);
		MONGO = mongodExecutable.start();
	}

	@Before
	public void deployVerticle(final TestContext context) throws IOException {
		vertx = Vertx.vertx();

		mongoConfiguration = new JsonObject().put("db_name", "cyface")
				.put("connection_string", "mongodb://localhost:" + MONGO_PORT);
		
		DeploymentOptions options = new DeploymentOptions().setConfig(mongoConfiguration);

		vertx.deployVerticle(SerializationVerticle.class, options, context.asyncAssertSuccess());
	}

	@After
	public void shutdown() {
		MONGO.stop();
	}

	@Test
	public void test(final TestContext context) {
		final Async async = context.async();
		
		EventBus eventBus = vertx.eventBus();
		eventBus.registerDefaultCodec(Measurement.class, Measurement.getCodec());
		eventBus.consumer(EventBusAddresses.MEASUREMENT_SAVED, new Handler<Message<String>>() {

			@Override
			public void handle(Message<String> event) {
				String generatedIdentifier = event.body();
				MongoClient client = MongoClient.createShared(vertx, mongoConfiguration);
				client.findOne("measurements", new JsonObject().put("_id", generatedIdentifier), null, object -> {
					context.assertTrue(object.succeeded());
					
					JsonObject json = object.result();
					String deviceIdentifier = json.getString(FormAttributes.DEVICE_ID.getValue());
					String measurementIdentifier = json.getString(FormAttributes.MEASUREMENT_ID.getValue());
					String operatingSystemVersion = json.getString(FormAttributes.OS_VERSION.getValue());
					String deviceType = json.getString(FormAttributes.DEVICE_TYPE.getValue());
					
					context.assertEquals("some_device", deviceIdentifier);
					context.assertEquals("2", measurementIdentifier);
					context.assertEquals("9.0.0", operatingSystemVersion);
					context.assertEquals("Pixel 2", deviceType);
					
					async.complete();
				});
			}
		});
		
		eventBus.publish(EventBusAddresses.NEW_MEASUREMENT, new Measurement("some_device", "2", "9.0.0", "Pixel 2", Collections.EMPTY_LIST));
		
		async.await(5_000L);
	}

}
