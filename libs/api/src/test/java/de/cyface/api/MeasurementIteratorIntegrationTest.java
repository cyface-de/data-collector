/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cyface.apitestutils.TestMongoDatabase;
import de.cyface.apitestutils.fixture.GeoLocationTestFixture;
import de.cyface.apitestutils.fixture.TestMeasurementDocument;
import de.cyface.model.MeasurementIdentifier;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests the integration of {@link MeasurementIterator} with MongoDB.
 *
 * @author Armin Schnabel
 * @version 6.6.0
 * @since 1.0.0
 */
@ExtendWith(VertxExtension.class)
public class MeasurementIteratorIntegrationTest {

    /**
     * The default data source name to use for user and data database if none is provided via configuration.
     */
    private final String DEFAULT_MONGO_DATA_SOURCE_NAME = "cyface";
    /**
     * The id of the user to add test data for.
     */
    private static final String TEST_USER_ID = "624d8c51c0879068499676c6";
    /**
     * A temporary Mongo database used only for one test.
     */
    private TestMongoDatabase testMongoDatabase;
    /**
     * The client to be used to access the test Mongo database.
     */
    private MongoClient dataClient;

    /**
     * Sets up a fresh test environment with a test mongo database before each test.
     *
     * @param vertx The Vertx instance used to initialize the server
     * @throws IOException If setting up the test Mongo database fails
     */
    @BeforeEach
    void setUp(final Vertx vertx) throws IOException {
        this.testMongoDatabase = new TestMongoDatabase();
        testMongoDatabase.start();

        // Set up a Mongo client to access the database
        final var mongoDbConfiguration = testMongoDatabase.config();
        // final var dataSourceName = config.getString("data_source_name", DEFAULT_MONGO_DATA_SOURCE_NAME);
        this.dataClient = MongoClient.createShared(vertx, mongoDbConfiguration/* , dataSourceName */);
    }

    /**
     * Does a cleanup of the test mongo database after each test.
     */
    @AfterEach
    public void tearDown() {
        testMongoDatabase.stop();
    }

    @Test
    void test(final VertxTestContext testContext) {
        // Arrange
        final var deviceIdentifier = UUID.randomUUID().toString();
        final var measurementId1 = 1L;
        final var measurementId2 = 2L;
        final var id1 = new MeasurementIdentifier(deviceIdentifier, measurementId1);
        final var id2 = new MeasurementIdentifier(deviceIdentifier, measurementId2);
        final var measurementIdentifiers = new MeasurementIdentifier[] {id1, id2};
        final var testDocuments = new ArrayList<TestMeasurementDocument>();
        testDocuments.add(new TestMeasurementDocument(TEST_USER_ID, measurementId1, deviceIdentifier));
        testDocuments.add(new TestMeasurementDocument(TEST_USER_ID, measurementId2, deviceIdentifier));
        final var fixture = new GeoLocationTestFixture(testDocuments);

        // Insert test data
        final var insertTestData = fixture.insertTestData(dataClient);
        insertTestData.onSuccess(succeeded -> {
            // Ensure the test data is there
            final var ids = Arrays.stream(measurementIdentifiers).map(id -> new JsonObject()
                    .put("metaData.deviceId", id.getDeviceIdentifier())
                    .put("metaData.measurementId", id.getMeasurementIdentifier())).collect(Collectors.toList());
            final var query = new JsonObject().put("$or", ids);
            final var testFind = dataClient.find("deserialized", query);
            testFind.onSuccess(result -> {
                if (result.size() != 2) {
                    testContext.failNow("Unexpected number of test documents");
                    return;
                }

                // Now create the ReadStream<Buckets>
                final var strategy = new MeasurementRetrievalWithoutSensorData();
                final var bucketStream = dataClient.findBatchWithOptions("deserialized", query, strategy.findOptions());

                // Act: load measurements
                final var returnedMeasurements = testContext.checkpoint(2);
                new MeasurementIterator(bucketStream, strategy, testContext::failNow, iterator -> {

                    // Assert: correct amount of measurements returned by the iterator
                    iterator.load(
                            measurement -> returnedMeasurements.flag(),
                            separation -> {
                            },
                            ended -> {
                            },
                            testContext::failNow);
                });
            });
            testFind.onFailure(testContext::failNow);
        });
        insertTestData.onFailure(testContext::failNow);
    }
}
