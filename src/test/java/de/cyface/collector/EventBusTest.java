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
 * @version 1.1.3
 * @since 2.0.0
 */
@RunWith(VertxUnitRunner.class)
@SuppressWarnings("PMD.MethodNamingConventions")
public final class EventBusTest {
    /**
     * The logger used by objects of this class. Configure it using <tt>/src/test/resources/logback-test.xml</tt> and do
     * not forget to set the Java property:
     * <tt>-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory</tt>.
     */
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBusTest.class);
    /**
     * Th version of the app used for the test measurements.
     */
    private static final String TEST_DEVICE_APP_VERSION = "4.0.0-alpha1";
    /**
     * The device name used for the test measurements.
     */
    private static final String TEST_DEVICE_NAME = "Pixel 2";
    /**
     * The default operating system version used by test measurements.
     */
    private static final String TEST_DEVICE_OS_VERSION = "9.0.0";
    /**
     * A random device identifier used as test data.
     */
    private static final String TEST_DEVICE_IDENTIFIER = UUID.randomUUID().toString();
    /**
     * Process providing a connection to the test Mongo database.
     */
    private transient MongoTest mongoTest;
    /**
     * The <code>Vertx</code> used for testing the verticles.
     */
    private transient Vertx vertx;
    /**
     * The configuration used for starting the Mongo database under test.
     */
    private transient JsonObject mongoConfiguration;

    /**
     * Deploys the {@link SerializationVerticle} for testing purposes.
     *
     * @param context The Vert.x context used for testing
     * @throws IOException Fails the test if anything unexpected happens
     */
    @Before
    public void deployVerticle(final TestContext context) throws IOException {
        mongoTest = new MongoTest();
        try (ServerSocket socket = new ServerSocket(0);) {
            final int mongoPort = socket.getLocalPort();
            socket.close();
            mongoTest.setUpMongoDatabase(mongoPort);

            vertx = Vertx.vertx();

            mongoConfiguration = new JsonObject().put("db_name", "cyface").put("connection_string",
                    "mongodb://localhost:" + mongoPort);

            final DeploymentOptions options = new DeploymentOptions().setConfig(mongoConfiguration);

            vertx.deployVerticle(SerializationVerticle.class, options, context.asyncAssertSuccess());
        }
    }

    /**
     * Finishes the test <code>Vertx</code> instance and stops the Mongo database.
     *
     * @param context The Vert.x context used for testing
     */
    @After
    public void shutdown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
        mongoTest.stopMongoDb();
    }

    /**
     * Tests if the {@link SerializationVerticle} handles new measurements arriving in the system correctly.
     *
     * @param context The Vert.x context used for testing
     * @throws IOException If working with the data files fails
     */
    @Test
    public void test(final TestContext context) throws IOException {
        final Async async = context.async();

        final EventBus eventBus = vertx.eventBus();
        eventBus.registerDefaultCodec(Measurement.class, Measurement.getCodec());
        eventBus.consumer(EventBusAddressUtils.MEASUREMENT_SAVED, new Handler<Message<String>>() {

            @Override
            public void handle(final Message<String> event) {
                final ExpectedData expectedDeviceData = new ExpectedData(TEST_DEVICE_IDENTIFIER, TEST_DEVICE_OS_VERSION,
                        TEST_DEVICE_NAME,
                        TEST_DEVICE_APP_VERSION);
                final ExpectedMeasurementData expectedMeasurementData = new ExpectedMeasurementData("2", 200.0, 10L,
                        10.0, 10.0, 10_000L, 12.0, 12.0, 12_000L);

                checkMeasurementData(event.body(), context, async, expectedDeviceData, expectedMeasurementData);
            }
        });

        eventBus.publish(EventBusAddressUtils.NEW_MEASUREMENT,
                new Measurement(TEST_DEVICE_IDENTIFIER, "2", TEST_DEVICE_OS_VERSION, TEST_DEVICE_NAME, TEST_DEVICE_APP_VERSION, 200.0,
                        10,
                        new GeoLocation(10.0, 10.0, 10_000), new GeoLocation(12.0, 12.0, 12_000), "BICYCLE", "testUser",
                        fixtureUploads()));

        async.await(5_000L);
    }

    /**
     * Tests that handling measurements without geo locations works as expected.
     *
     * @param context The Vert.x context used for testing
     * @throws IOException If working with the data files fails
     */
    @Test
    public void testPublishMeasurementWithNoGeoLocations_HappyPath(final TestContext context) throws IOException {
        final Async async = context.async();

        final EventBus eventBus = vertx.eventBus();
        eventBus.registerDefaultCodec(Measurement.class, Measurement.getCodec());
        eventBus.consumer(EventBusAddressUtils.MEASUREMENT_SAVED, new Handler<Message<String>>() {

            @Override
            public void handle(final Message<String> event) {
                final ExpectedData expectedDeviceData = new ExpectedData(TEST_DEVICE_IDENTIFIER, TEST_DEVICE_OS_VERSION,
                        TEST_DEVICE_NAME,
                        TEST_DEVICE_APP_VERSION);
                final ExpectedMeasurementData expectedMeasurementData = new ExpectedMeasurementData("2", .0, 0L, null,
                        null, null, null, null, null);

                checkMeasurementData(event.body(), context, async, expectedDeviceData, expectedMeasurementData);
            }
        });

        eventBus.publish(EventBusAddressUtils.NEW_MEASUREMENT,
                new Measurement(TEST_DEVICE_IDENTIFIER, "2", TEST_DEVICE_OS_VERSION, TEST_DEVICE_NAME,
                        TEST_DEVICE_APP_VERSION, .0, 0L, null, null, "BICYCLE", "testUser", fixtureUploads()));

        async.await(5_000L);
    }

    /**
     * Provides a fixture simulating the files necessary to create an appropriate measurement.
     *
     * @return A fixture containing empty files for data and events
     * @throws IOException If writing the empty temporary files fails for some reason
     */
    private List<Measurement.FileUpload> fixtureUploads() throws IOException {
        final File dataFile = File.createTempFile("data", "ccyf");
        final File eventsFile = File.createTempFile("events", "ccyfe");
        return Arrays.asList(new Measurement.FileUpload(dataFile, "ccyf"),
                new Measurement.FileUpload(eventsFile, "ccyfe"));
    }

    /**
     * Asserts the meta data on the uploaded measurement.
     *
     * @param identifier The measurement identifier as a <code>String</code> in the format "deviceId:measurementId"
     * @param context The Vertx <code>TestContext</code>
     * @param async A Vertx synchronizer, which is waiting for the assertions to finish
     * @param expectedDeviceData The meta data of the device that uploaded the test measurement to check
     * @param expectedMeasurementData The meta data of the test measurement to check
     */
    private void checkMeasurementData(final String identifier, final TestContext context, final Async async,
            final ExpectedData expectedDeviceData, final ExpectedMeasurementData expectedMeasurementData) {
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

                context.assertEquals(expectedDeviceData.deviceIdentifier(), deviceIdentifier);
                context.assertEquals(expectedMeasurementData.measurementIdentifier(), measurementIdentifier);
                context.assertEquals(expectedDeviceData.operatingSystemVersion(), operatingSystemVersion);
                context.assertEquals(expectedDeviceData.deviceType(), deviceType);
                context.assertEquals(expectedDeviceData.applicationVersion(), applicationVersion);
                context.assertEquals(expectedMeasurementData.length(), length);
                context.assertEquals(expectedMeasurementData.locationCount(), locationCount);
                context.assertEquals(expectedMeasurementData.startLocationLat(), startLocationLat);
                context.assertEquals(expectedMeasurementData.startLocationLon(), startLocationLon);
                context.assertEquals(expectedMeasurementData.startLocationTimestamp(), startLocationTimestamp);
                context.assertEquals(expectedMeasurementData.endLocationLat(), endLocationLat);
                context.assertEquals(expectedMeasurementData.endLocationLon(), endLocationLon);
                context.assertEquals(expectedMeasurementData.endLocationTimestamp(), endLocationTimestamp);

            } catch (Exception e) {
                context.fail(e);
            } finally {
                async.complete();
            }
        });
    }

    /**
     * A parameter object to wrap all meta information that is checked for a single uploading device.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    private static final class ExpectedData {
        /**
         * The world wide unique identifier of the uploading device.
         */
        private final String deviceIdentifier;
        /**
         * The version of the uploading operating system (usually Android or iOS).
         */
        private final String operatingSystemVersion;
        /**
         * The type of the uploading device. This should usually be some kind of Android or iOS phone.
         */
        private final String deviceType;
        /**
         * The version of the Cyface application/SDK uploading the data.
         */
        private final String applicationVersion;

        /**
         * Creates a new completely intialized object of this class.
         *
         * @param deviceIdentifier The world wide unique identifier of the uploading device
         * @param operatingSystemVersion The version of the uploading operating system (usually Android or iOS)
         * @param deviceType The type of the uploading device. This should usually be some kind of Android or iOS phone
         * @param applicationVersion The version of the Cyface application/SDK uploading the data
         */
        ExpectedData(final String deviceIdentifier, final String operatingSystemVersion, final String deviceType,
                final String applicationVersion) {
            this.deviceIdentifier = deviceIdentifier;
            this.operatingSystemVersion = operatingSystemVersion;
            this.deviceType = deviceType;
            this.applicationVersion = applicationVersion;
        }

        /**
         * @return The world wide unique identifier of the uploading device
         */
        public String deviceIdentifier() {
            return deviceIdentifier;
        }

        /**
         * @return The version of the uploading operating system (usually Android or iOS)
         */
        public String operatingSystemVersion() {
            return operatingSystemVersion;
        }

        /**
         * @return The type of the uploading device. This should usually be some kind of Android or iOS phone
         */
        public String deviceType() {
            return deviceType;
        }

        /**
         * @return The version of the Cyface application/SDK uploading the data
         */
        public String applicationVersion() {
            return applicationVersion;
        }

    }

    /**
     * A value object containing all expected meta data a test is run against for a single Cyface measurement. All
     * attributes are
     * typed with object types, since they can become <code>null</code>.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    private static final class ExpectedMeasurementData {
        /**
         * The device wide unqiue identifier of the measurement.
         */
        private final String measurementIdentifier;
        /**
         * The length of the uploaded track in meters.
         */
        private final Double length;
        /**
         * The count of captured locations in the uploaded track.
         */
        private final Long locationCount;
        /**
         * The geographical latitude of the first location in the uploaded track.
         */
        private final Double startLocationLat;
        /**
         * The geographical longitude of the first location in the uploaded track.
         */
        private final Double startLocationLon;
        /**
         * The unix timestamp in milliseconds of the first location in the uploaded track.
         */
        private final Long startLocationTimestamp;
        /**
         * The geographical latitude of the last location in the uploaded track.
         */
        private final Double endLocationLat;
        /**
         * The geographical longitude of the last location in the uploaded track.
         */
        private final Double endLocationLon;
        /**
         * The unix timestamp in milliseconds of the last location in the uploaded track.
         */
        private final Long endLocationTimestamp;

        /**
         * Creates a new completely initialized object of this class.
         *
         * @param measurementIdentifier The device wide unqiue identifier of the measurement
         * @param length The length of the uploaded track in meters
         * @param locationCount The count of captured locations in the uploaded track
         * @param startLocationLat The geographical latitude of the first location in the uploaded track
         * @param startLocationLon The geographical longitude of the first location in the uploaded track
         * @param startLocationTimestamp The unix timestamp in milliseconds of the first location in the uploaded track
         * @param endLocationLat The geographical latitude of the last location in the uploaded track
         * @param endLocationLon The geographical longitude of the last location in the uploaded track
         * @param endLocationTimestamp The unix timestamp in milliseconds of the last location in the uploaded track
         */
        ExpectedMeasurementData(final String measurementIdentifier, final Double length,
                final Long locationCount, final Double startLocationLat, final Double startLocationLon,
                final Long startLocationTimestamp, final Double endLocationLat, final Double endLocationLon,
                final Long endLocationTimestamp) {
            this.measurementIdentifier = measurementIdentifier;
            this.length = length;
            this.locationCount = locationCount;
            this.startLocationLat = startLocationLat;
            this.startLocationLon = startLocationLon;
            this.startLocationTimestamp = startLocationTimestamp;
            this.endLocationLat = endLocationLat;
            this.endLocationLon = endLocationLon;
            this.endLocationTimestamp = endLocationTimestamp;
        }

        /**
         * @return The device wide unqiue identifier of the measurement
         */
        public String measurementIdentifier() {
            return measurementIdentifier;
        }

        /**
         * @return The length of the uploaded track in meters
         */
        public Double length() {
            return length;
        }

        /**
         * @return The count of captured locations in the uploaded track
         */
        public Long locationCount() {
            return locationCount;
        }

        /**
         * @return The geographical latitude of the first location in the uploaded track
         */
        public Double startLocationLat() {
            return startLocationLat;
        }

        /**
         * @return The geographical longitude of the first location in the uploaded track
         */
        public Double startLocationLon() {
            return startLocationLon;
        }

        /**
         * @return The unix timestamp in milliseconds of the first location in the uploaded track
         */
        public Long startLocationTimestamp() {
            return startLocationTimestamp;
        }

        /**
         * @return The geographical latitude of the last location in the uploaded track
         */
        public Double endLocationLat() {
            return endLocationLat;
        }

        /**
         * @return The geographical longitude of the last location in the uploaded track
         */
        public Double endLocationLon() {
            return endLocationLon;
        }

        /**
         * @return The unix timestamp in milliseconds of the last location in the uploaded track
         */
        public Long endLocationTimestamp() {
            return endLocationTimestamp;
        }

    }

}
