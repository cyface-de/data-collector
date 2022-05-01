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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.cyface.apitestutils.TestMongoDatabase;
import de.cyface.apitestutils.fixture.GeoLocationTestFixture;
import de.cyface.apitestutils.fixture.TestFixture;
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
        this.dataClient = MongoClient.createShared(vertx, mongoDbConfiguration, "cyface");
    }

    /**
     * Does a cleanup of the test mongo database after each test.
     */
    @AfterEach
    public void tearDown() {
        testMongoDatabase.stop();
    }

    @ParameterizedTest
    @MethodSource("testParameters")
    void test(final TestParameters parameters, final VertxTestContext testContext) {
        // Arrange

        // Insert test data
        final var insert = parameters.fixture.insertTestData(dataClient);
        insert.onSuccess(succeeded -> {
            // Ensure the test data is there
            final var ids = parameters.measurementIdentifiers.stream().map(id -> new JsonObject()
                    .put("metaData.deviceId", id.getDeviceIdentifier())
                    .put("metaData.measurementId", id.getMeasurementIdentifier())).collect(Collectors.toList());
            final var query = ids.size() > 0 ? new JsonObject().put("$or", ids) : new JsonObject();
            final var testFind = dataClient.find("deserialized", query);
            testFind.onSuccess(result -> {
                if (result.size() != parameters.expectedResults) {
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
                            measurement -> {
                                if (parameters.expectedResults > 0) {
                                    returnedMeasurements.flag();
                                } else {
                                    testContext.failNow("No measurement bucket expected!");
                                }
                            },
                            separation -> {
                            },
                            ended -> {
                                if (parameters.expectedResults == 0) {
                                    testContext.completeNow();
                                }
                            },
                            testContext::failNow);
                });
            });
            testFind.onFailure(testContext::failNow);
        });
        insert.onFailure(testContext::failNow);
    }

    static Stream<TestParameters> testParameters() {

        final var deviceId = UUID.randomUUID().toString();
        final var measurementIdentifiers = new ArrayList<MeasurementIdentifier>();
        measurementIdentifiers.add(new MeasurementIdentifier(deviceId, 1L));
        measurementIdentifiers.add(new MeasurementIdentifier(deviceId, 2L));

        return Stream.of(
                new TestParameters(new GeoLocationTestFixture(measurementIdentifiers), measurementIdentifiers, 2),
                new TestParameters(new GeoLocationTestFixture(new ArrayList<>()), new ArrayList<>(), 0));
    }

    private static class TestParameters {
        TestFixture fixture;
        List<MeasurementIdentifier> measurementIdentifiers;
        int expectedResults;

        public TestParameters(TestFixture fixture, ArrayList<MeasurementIdentifier> measurementIdentifiers,
                int expectedResults) {
            this.fixture = fixture;
            this.measurementIdentifiers = measurementIdentifiers;
            this.expectedResults = expectedResults;
        }
    }
}
