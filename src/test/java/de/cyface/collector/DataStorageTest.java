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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    /**
     * Starts a test in memory Mongo database.
     *
     * @throws IOException If creating the server fails
     */
    @BeforeEach
    void setUp() throws IOException {
        mongoTest = new MongoTest();
        mongoTest.setUpMongoDatabase(Network.getFreeServerPort());
    }

    /**
     * Stops the Mongo database.
     */
    @AfterEach
    void shutdown() {
        mongoTest.stopMongoDb();
    }

    /**
     * Tests that handling measurements without geo locations works as expected.
     *
     * @param vertx A <code>Vertx</code> instance used during the test
     * @param ctx The Vert.x context used for testing
     * @throws URISyntaxException If the test data location is invalid
     */
    @Test
    @DisplayName("Test storing data on happy path")
    public void testPublishMeasurementWithNoGeoLocations_HappyPath(final Vertx vertx, final VertxTestContext ctx)
            throws URISyntaxException {
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
                    assertThat("Expect to receive a file with more than zero bytes!", downloadedBytes,
                            is(greaterThan(0L)));
                    assertThat("Test for an example meta datum, but expected value was not found!",
                            metaData.getValue(FormAttributes.APPLICATION_VERSION.getValue()),
                            is(equalTo(TEST_DEVICE_APP_VERSION)));
                });
                ctx.completeNow();
            }));
        }).onFailure(ctx::failNow);
    }

    /**
     * @return Some meta data used for testing
     */
    private JsonObject fixtureMetaData() {
        final var ret = new JsonObject();
        ret.put(FormAttributes.DEVICE_ID.getValue(), TEST_DEVICE_IDENTIFIER);
        ret.put(FormAttributes.MEASUREMENT_ID.getValue(), 1);
        ret.put(FormAttributes.OS_VERSION.getValue(), TEST_DEVICE_OS_VERSION);
        ret.put(FormAttributes.DEVICE_TYPE.getValue(), TEST_DEVICE_NAME);
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
}
