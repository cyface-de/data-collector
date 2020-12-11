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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.commons.MongoTest;
import de.cyface.collector.handler.FormAttributes;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.GridFsUploadOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests that storing data to an underlying Mongo database works.
 *
 * @author Klemens Muthmann
 * @version 1.1.3
 * @since 2.0.0
 */
@ExtendWith(VertxExtension.class)
@SuppressWarnings("PMD.MethodNamingConventions")
public final class DataStorageTest {
    /**
     * The logger used by objects of this class. Configure it using <tt>/src/test/resources/logback-test.xml</tt> and do
     * not forget to set the Java property:
     * <tt>-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory</tt>.
     */
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(DataStorageTest.class);
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

    @BeforeEach
    void setUp() throws IOException {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase(Network.getFreeServerPort());
    }

    /**
     * Finishes the test <code>Vertx</code> instance and stops the Mongo database.
     */
    @AfterEach
    void shutdown() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests that handling measurements without geo locations works as expected.
     *
     * @param ctx The Vert.x context used for testing
     * @throws IOException If working with the data files fails
     */
    @Test
    public void testPublishMeasurementWithNoGeoLocations_HappyPath(final Vertx vertx, final VertxTestContext ctx)
            throws IOException, URISyntaxException {
        final var client = MongoClient.createShared(vertx, mongoTest.clientConfiguration());
        client.createDefaultGridFsBucketService();

        final var gridFsBucketCreationFuture = client.createDefaultGridFsBucketService();
        final var uploads = List.of(Path.of(this.getClass().getResource("/iphone-neu.ccyf").toURI()));

        gridFsBucketCreationFuture.onSuccess(gridFsClient -> {
            final var fileSystem = vertx.fileSystem();
            final List<Future> fileUploadFutures = uploads.stream().map(fileUpload -> {
                final var fileOpenFuture = fileSystem.open(fileUpload.toAbsolutePath().toString(),
                        new OpenOptions());
                final var uploadFuture = fileOpenFuture.compose(asyncFile -> {

                    final var gridFsOptions = new GridFsUploadOptions();
                    gridFsOptions.setMetadata(fixtureMetaData());

                    return gridFsClient.uploadByFileNameWithOptions(asyncFile,
                            fileUpload.getFileName().toString(), gridFsOptions);
                });
                return (Future)uploadFuture;
            }).collect(Collectors.toList());

            fileUploadFutures.add(fileSystem.createTempFile("de.cyface.collector", "test"));
            CompositeFuture.all(fileUploadFutures).compose(result -> {
                final var uploadedFileId = (String)result.resultAt(0);
                final var tempFileName = (String)result.list().get(result.list().size() - 1);
                return CompositeFuture.all(gridFsClient.downloadFileByID(uploadedFileId, tempFileName),
                        client.find("fs.files", new JsonObject().put("_id",
                                new JsonObject().put("$oid", new ObjectId(uploadedFileId).toHexString()))));
            }).onComplete(ctx.succeeding(loadedData -> {
                final var downloadedBytes = (Long)loadedData.resultAt(0);
                final var metaData = ((List<JsonObject>)loadedData.resultAt(1)).get(0).getJsonObject("metadata");
                ctx.verify(() -> {
                    assertThat(downloadedBytes, is(greaterThan(0L)));
                    assertThat(metaData.getValue(FormAttributes.APPLICATION_VERSION.getValue()),
                            is(equalTo(TEST_DEVICE_APP_VERSION)));
                });
                ctx.completeNow();
            }));
        }).onFailure(ctx::failNow);
    }

    private JsonObject fixtureMetaData() {
        final var ret = new JsonObject();
        ret.put(FormAttributes.DEVICE_ID.getValue(), TEST_DEVICE_IDENTIFIER);
        ret.put(FormAttributes.MEASUREMENT_ID.getValue(), 1);
        ret.put(FormAttributes.OS_VERSION.getValue(), TEST_DEVICE_OS_VERSION);
        ret.put(FormAttributes.DEVICE_TYPE.getValue(), null);
        ret.put(FormAttributes.APPLICATION_VERSION.getValue(), TEST_DEVICE_APP_VERSION);
        ret.put(FormAttributes.LENGTH.getValue(), 0);
        ret.put(FormAttributes.LOCATION_COUNT.getValue(), 0);
        /*
         * if (locationCount > 0) {
         * ret.put(FormAttributes.START_LOCATION_LAT.getValue(), startLocation.getLat());
         * ret.put(FormAttributes.START_LOCATION_LON.getValue(), startLocation.getLon());
         * ret.put(FormAttributes.START_LOCATION_TS.getValue(), startLocation.getTimestamp());
         * ret.put(FormAttributes.END_LOCATION_LAT.getValue(), endLocation.getLat());
         * ret.put(FormAttributes.END_LOCATION_LON.getValue(), endLocation.getLon());
         * ret.put(FormAttributes.END_LOCATION_TS.getValue(), endLocation.getTimestamp());
         * }
         */
        ret.put(FormAttributes.VEHICLE_TYPE.getValue(), "BICYCLE");
        ret.put(FormAttributes.USERNAME.getValue(), "test");

        return ret;
    }

    /**
     * Asserts the meta data on the uploaded measurement.
     *
     * @param identifier The measurement identifier as a <code>String</code> in the format "deviceId:measurementId"
     * @param context The Vertx <code>TestContext</code>
     * @param expectedDeviceData The meta data of the device that uploaded the test measurement to check
     * @param expectedMeasurementData The meta data of the test measurement to check
     */
    private void checkMeasurementData(final String identifier, final Vertx vertx, final VertxTestContext context,
            final ExpectedData expectedDeviceData, final ExpectedMeasurementData expectedMeasurementData) {
        final var generatedIdentifier = identifier.split(":");
        final var client = MongoClient.createShared(vertx, mongoTest.clientConfiguration());
        final var query = new JsonObject();

        query.put("metadata.deviceId", generatedIdentifier[0]);
        query.put("metadata.measurementId", generatedIdentifier[1]);

        client.findOne("fs.files", query, null, context.succeeding(json -> context.verify(() -> {
            assertThat(json, is(not(nullValue())));
            final var metadata = json.getJsonObject("metadata");
            assertThat(metadata, is(not(nullValue())));

            final var deviceIdentifier = metadata.getString(FormAttributes.DEVICE_ID.getValue());
            final var measurementIdentifier = metadata
                    .getString(FormAttributes.MEASUREMENT_ID.getValue());
            final var operatingSystemVersion = metadata.getString(FormAttributes.OS_VERSION.getValue());
            final var deviceType = metadata.getString(FormAttributes.DEVICE_TYPE.getValue());
            final var applicationVersion = metadata
                    .getString(FormAttributes.APPLICATION_VERSION.getValue());
            final var length = metadata.getDouble(FormAttributes.LENGTH.getValue());
            final var locationCount = metadata.getLong(FormAttributes.LOCATION_COUNT.getValue());
            final var startLocationLat = metadata
                    .getDouble(FormAttributes.START_LOCATION_LAT.getValue());
            final var startLocationLon = metadata
                    .getDouble(FormAttributes.START_LOCATION_LON.getValue());
            final var startLocationTimestamp = metadata
                    .getLong(FormAttributes.START_LOCATION_TS.getValue());
            final var endLocationLat = metadata.getDouble(FormAttributes.END_LOCATION_LAT.getValue());
            final var endLocationLon = metadata.getDouble(FormAttributes.END_LOCATION_LON.getValue());
            final var endLocationTimestamp = metadata.getLong(FormAttributes.END_LOCATION_TS.getValue());

            assertThat(expectedDeviceData.deviceIdentifier(), is(deviceIdentifier));
            assertThat(expectedMeasurementData.measurementIdentifier(), is(measurementIdentifier));
            assertThat(expectedDeviceData.operatingSystemVersion(), is(operatingSystemVersion));
            assertThat(expectedDeviceData.deviceType(), is(deviceType));
            assertThat(expectedDeviceData.applicationVersion(), is(applicationVersion));
            assertThat(expectedMeasurementData.length(), is(length));
            assertThat(expectedMeasurementData.locationCount(), is(locationCount));
            assertThat(expectedMeasurementData.startLocationLat(), is(startLocationLat));
            assertThat(expectedMeasurementData.startLocationLon(), is(startLocationLon));
            assertThat(expectedMeasurementData.startLocationTimestamp(), is(startLocationTimestamp));
            assertThat(expectedMeasurementData.endLocationLat(), is(endLocationLat));
            assertThat(expectedMeasurementData.endLocationLon(), is(endLocationLon));
            assertThat(expectedMeasurementData.endLocationTimestamp(), is(endLocationTimestamp));

            context.completeNow();
        })));
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
