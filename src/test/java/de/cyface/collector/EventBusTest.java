/*
 * Copyright 2018,2019 Cyface GmbH
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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.cyface.collector.handler.FormAttributes;
import de.cyface.collector.model.GeoLocation;
import de.cyface.collector.model.Measurement;
import de.cyface.collector.verticle.SerializationVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests individual verticles by sending appropriate messages using the Vert.x event bus.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.0.0
 */
@RunWith(VertxUnitRunner.class)
public final class EventBusTest {

    /**
     * The logger used by objects of this class. Configure it using <tt>/src/test/resources/logback-test.xml</tt> and do
     * not forget to set the Java property:
     * <tt>-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory</tt>.
     */
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBusTest.class);
    /**
     * A random device identifier used as test data.
     */
    private static final String TEST_DEVICE_IDENTIFIER = UUID.randomUUID().toString();
    /**
     * Process providing a connection to the test Mongo database.
     */
    private MongoTest mongoTest;
    /**
     * The <code>Vertx</code> used for testing the verticles.
     */
    private Vertx vertx;
    /**
     * The configuration used for starting the Mongo database under test.
     */
    private JsonObject mongoConfiguration;

    /**
     * Deploys the {@link SerializationVerticle} for testing purposes.
     *
     * @param context The Vert.x context used for testing.
     * @throws IOException Fails the test if anything unexpected happens.
     */
    @Before
    public void deployVerticle(final TestContext context) throws IOException {
        mongoTest = new MongoTest();
        ServerSocket socket = new ServerSocket(0);
        int mongoPort = socket.getLocalPort();
        socket.close();
        mongoTest.setUpMongoDatabase(mongoPort);

        vertx = Vertx.vertx();

        mongoConfiguration = new JsonObject().put("db_name", "cyface").put("connection_string",
                "mongodb://localhost:" + mongoPort);

        DeploymentOptions options = new DeploymentOptions().setConfig(mongoConfiguration);

        vertx.deployVerticle(SerializationVerticle.class, options, context.asyncAssertSuccess());
    }

    /**
     * Finishes the test <code>Vertx</code> instance and stops the Mongo database.
     *
     * @param context The Vert.x context used for testing.
     */
    @After
    public void shutdown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
        mongoTest.stopMongoDb();
    }

    /**
     * Tests if the {@link SerializationVerticle} handles new measurements arriving in the system correctly.
     *
     * @param context The Vert.x context used for testing.
     * @throws IOException If working with the data files fails.
     */
    @Test
    public void test(final TestContext context) throws IOException {
        final Async async = context.async();

        final EventBus eventBus = vertx.eventBus();
        eventBus.registerDefaultCodec(Measurement.class, Measurement.getCodec());
        eventBus.consumer(EventBusAddresses.MEASUREMENT_SAVED, new Handler<Message<String>>() {

            @Override
            public void handle(Message<String> event) {
                final ExpectedData expectedData = new ExpectedData();
                expectedData.deviceIdentifier = TEST_DEVICE_IDENTIFIER;
                expectedData.measurementIdentifier = "2";
                expectedData.operatingSystemVersion = "9.0.0";
                expectedData.deviceType = "Pixel 2";
                expectedData.applicationVersion = "4.0.0-alpha1";
                expectedData.length = 200.0;
                expectedData.locationCount = 10L;
                expectedData.startLocationLat = 10.0;
                expectedData.startLocationLon = 10.0;
                expectedData.startLocationTimestamp = 10_000L;
                expectedData.endLocationLat = 12.0;
                expectedData.endLocationLon = 12.0;
                expectedData.endLocationTimestamp = 12_000L;

                checkMeasurementData(event.body(), context, async, expectedData);
            }
        });

        eventBus.publish(EventBusAddresses.NEW_MEASUREMENT,
                new Measurement(TEST_DEVICE_IDENTIFIER, "2", "9.0.0", "Pixel 2", "4.0.0-alpha1", 200.0, 10,
                        new GeoLocation(10.0, 10.0, 10_000), new GeoLocation(12.0, 12.0, 12_000), "BICYCLE", "testUser",
                        fixtureUploads()));

        async.await(5_000L);
    }

    /**
     * Tests that handling measurements without geo locations works as expected.
     *
     * @param context The Vert.x context used for testing.
     * @throws IOException If working with the data files fails.
     */
    @Test
    public void testPublishMeasurementWithNoGeoLocations_HappyPath(final TestContext context) throws IOException {
        final Async async = context.async();

        final EventBus eventBus = vertx.eventBus();
        eventBus.registerDefaultCodec(Measurement.class, Measurement.getCodec());
        eventBus.consumer(EventBusAddresses.MEASUREMENT_SAVED, new Handler<Message<String>>() {

            @Override
            public void handle(Message<String> event) {
                final ExpectedData expectedData = new ExpectedData();

                expectedData.deviceIdentifier = TEST_DEVICE_IDENTIFIER;
                expectedData.measurementIdentifier = "2";
                expectedData.operatingSystemVersion = "9.0.0";
                expectedData.deviceType = "Pixel 2";
                expectedData.applicationVersion = "4.0.0-alpha1";
                expectedData.length = .0;
                expectedData.locationCount = 0L;
                expectedData.startLocationLat = null;
                expectedData.startLocationLon = null;
                expectedData.startLocationTimestamp = null;
                expectedData.endLocationLat = null;
                expectedData.endLocationLon = null;
                expectedData.endLocationTimestamp = null;

                checkMeasurementData(event.body(), context, async, expectedData);
            }
        });

        eventBus.publish(EventBusAddresses.NEW_MEASUREMENT,
                new Measurement(TEST_DEVICE_IDENTIFIER, "2", "9.0.0", "Pixel 2",
                        "4.0.0-alpha1", .0, 0L, null, null, "BICYCLE", "testUser", fixtureUploads()));

        async.await(5_000L);
    }

    /**
     * Provides a fixture simulating the files necessary to create an appropriate measurement.
     *
     * @return A fixture containing empty files for data and events.
     * @throws IOException If writing the empty temporary files fails for some reason.
     */
    private List<Measurement.FileUpload> fixtureUploads() throws IOException {
        final File dataFile = File.createTempFile("data", "ccyf");
        final File eventsFile = File.createTempFile("events", "ccyfe");
        return Arrays.asList(new Measurement.FileUpload(dataFile, "ccyf"),
                new Measurement.FileUpload(eventsFile, "ccyfe"));
    }

    private void checkMeasurementData(final String identifier, final TestContext context, final Async async,
            final ExpectedData expectedData) {
        final String[] generatedIdentifier = identifier.split(":");
        final MongoClient client = MongoClient.createShared(vertx, mongoConfiguration);
        final JsonObject query = new JsonObject();

        query.put("metadata.deviceId", generatedIdentifier[0]);
        query.put("metadata.measurementId", generatedIdentifier[1]);

        client.findOne("fs.files", query, null, object -> {
            context.assertTrue(object.succeeded());

            final JsonObject json = object.result();
            context.assertNotNull(json);
            final JsonObject metadata = json.getJsonObject("metadata");
            context.assertNotNull(metadata);

            try {
                final String deviceIdentifier = metadata.getString(FormAttributes.DEVICE_ID.getValue());
                final String measurementIdentifier = metadata
                        .getString(FormAttributes.MEASUREMENT_ID.getValue());
                final String operatingSystemVersion = metadata.getString(FormAttributes.OS_VERSION.getValue());
                final String deviceType = metadata.getString(FormAttributes.DEVICE_TYPE.getValue());
                final String applicationVersion = metadata
                        .getString(FormAttributes.APPLICATION_VERSION.getValue());
                final double length = metadata.getDouble(FormAttributes.LENGTH.getValue());
                final long locationCount = metadata.getLong(FormAttributes.LOCATION_COUNT.getValue());
                final Double startLocationLat = metadata
                        .getDouble(FormAttributes.START_LOCATION_LAT.getValue());
                final Double startLocationLon = metadata
                        .getDouble(FormAttributes.START_LOCATION_LON.getValue());
                final Long startLocationTimestamp = metadata
                        .getLong(FormAttributes.START_LOCATION_TS.getValue());
                final Double endLocationLat = metadata.getDouble(FormAttributes.END_LOCATION_LAT.getValue());
                final Double endLocationLon = metadata.getDouble(FormAttributes.END_LOCATION_LON.getValue());
                final Long endLocationTimestamp = metadata.getLong(FormAttributes.END_LOCATION_TS.getValue());

                context.assertEquals(expectedData.deviceIdentifier, deviceIdentifier);
                context.assertEquals(expectedData.measurementIdentifier, measurementIdentifier);
                context.assertEquals(expectedData.operatingSystemVersion, operatingSystemVersion);
                context.assertEquals(expectedData.deviceType, deviceType);
                context.assertEquals(expectedData.applicationVersion, applicationVersion);
                context.assertEquals(expectedData.length, length);
                context.assertEquals(expectedData.locationCount, locationCount);
                context.assertEquals(expectedData.startLocationLat, startLocationLat);
                context.assertEquals(expectedData.startLocationLon, startLocationLon);
                context.assertEquals(expectedData.startLocationTimestamp, startLocationTimestamp);
                context.assertEquals(expectedData.endLocationLat, endLocationLat);
                context.assertEquals(expectedData.endLocationLon, endLocationLon);
                context.assertEquals(expectedData.endLocationTimestamp, endLocationTimestamp);

            } catch (Exception e) {
                context.fail(e);
            } finally {
                async.complete();
            }
        });
    }

    private class ExpectedData {
        String deviceIdentifier;
        String measurementIdentifier;
        String operatingSystemVersion;
        String deviceType;
        String applicationVersion;
        Double length;
        Long locationCount;
        Double startLocationLat;
        Double startLocationLon;
        Long startLocationTimestamp;
        Double endLocationLat;
        Double endLocationLon;
        Long endLocationTimestamp;
    }

}
