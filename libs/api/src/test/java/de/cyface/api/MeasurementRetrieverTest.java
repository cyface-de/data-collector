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

import de.cyface.model.MeasurementIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import de.cyface.apitestutils.TestMongoDatabase;
import de.cyface.apitestutils.fixture.GeoLocationTestFixture;
import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests the integration of {@link MeasurementRetriever} with MongoDB.
 *
 * @author Armin Schnabel
 * @version 6.6.0
 * @since 1.0.0
 */
@ExtendWith(VertxExtension.class)
public class MeasurementRetrieverTest {

    /**
     * A temporary Mongo database used only for one test.
     */
    private TestMongoDatabase testMongoDatabase;
    /**
     * The client to be used to access the test Mongo database.
     */
    private MongoClient mongoClient;
    /**
     * The instance under test.
     */
    private MeasurementRetriever oocut;

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
        this.mongoClient = MongoClient.createShared(vertx, mongoDbConfiguration, "cyface");
    }

    /**
     * Does a cleanup of the test mongo database after each test.
     */
    @AfterEach
    public void tearDown() {
        if (testMongoDatabase != null) {
            testMongoDatabase.stop();
        }
    }

    @Test
    void test(final VertxTestContext testContext) {
        // Arrange
        final var deviceIdentifier = UUID.randomUUID().toString();
        final var measurementIdentifiers = new ArrayList<MeasurementIdentifier>();
        measurementIdentifiers.add(new MeasurementIdentifier(deviceIdentifier, 1L));
        measurementIdentifiers.add(new MeasurementIdentifier(deviceIdentifier, 2L));
        final var fixture = new GeoLocationTestFixture(measurementIdentifiers);
        final var future = fixture.insertTestData(mongoClient);
        future.onSuccess(ownerUserId -> {
            this.oocut = new MeasurementRetriever("deserialized", new MeasurementRetrievalWithoutSensorData(), List.of(new String[]{ownerUserId}));

            // Act
            final var result = oocut.loadMeasurements(mongoClient);

            // Assert
            final var returnedMeasurements = testContext.checkpoint(2);
            result.onSuccess(iterator -> {
                // Assert: correct amount of measurements returned by the iterator
                iterator.load(
                        measurement -> returnedMeasurements.flag(),
                        separation -> {
                        },
                        ended -> {
                        },
                        testContext::failNow);
            });
            result.onFailure(testContext::failNow);
        }).onFailure(testContext::failNow);
    }
}
