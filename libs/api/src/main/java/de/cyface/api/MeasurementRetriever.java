/*
 * Copyright 2020-2021 Cyface GmbH
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

import java.time.ZonedDateTime;
import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Loads deserialized measurements from the database for a vert.x context.
 *
 * @author Armin Schnabel
 * @since 6.0.0
 */
public class MeasurementRetriever {

    /**
     * The name of the collection in the Mongo database which stores the deserialized measurement data.
     * TODO: should be a configurable from outside (parameter)
     */
    private static final String DESERIALIZED_COLLECTION_NAME = "deserialized";
    /**
     * The {@link MeasurementRetrievalStrategy} to be used to load data from the database.
     */
    private final MeasurementRetrievalStrategy strategy;

    /**
     * Constructs a fully initialized object of this object.
     *
     * @param strategy the {@link MeasurementRetrievalStrategy} to be used when loading the measurements.
     */
    public MeasurementRetriever(final MeasurementRetrievalStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Loads all measurements of all users of the specified {@code userNames} from the database.
     *
     * @param userNames The names of the users of whom all measurements are to be loaded
     * @param dataClient The client to access the data from
     * @return a {@code Future} containing the users' {@code Measurement}s if successful
     */
    @SuppressWarnings("unused") // Part of the API
    public Future<MeasurementIterator> loadMeasurements(final List<String> userNames, final MongoClient dataClient) {
        return loadMeasurements(userNames, dataClient, null, null);
    }

    /**
     * Loads all measurements of all users of the specified {@code userNames} from the database.
     *
     * @param userNames The names of the users of whom all measurements are to be loaded
     * @param dataClient The client to access the data from
     * @param startTime The value is {@code null} or a point in time to only return newer results.
     * @param endTime The value is {@code null} or a point in time to only return older results.
     * @return a {@code Future} containing the {@link MeasurementIterator} with the users' data if successful
     */
    public Future<MeasurementIterator> loadMeasurements(final List<String> userNames, final MongoClient dataClient,
                                                        final ZonedDateTime startTime, final ZonedDateTime endTime) {

        final var query = new JsonObject();
        query.put("metaData.username", new JsonObject().put("$in", new JsonArray(userNames)));
        query.put("metaData.sensorType", "location");
        /*if (startTime != null || endTime != null) {
            final var timeRestriction = new JsonObject();
            if (startTime != null) {
                timeRestriction.put("$gt", new JsonObject().put("$date", startTime.toString()));
            }
            if (endTime != null) {
                timeRestriction.put("$lt", new JsonObject().put("$date", endTime.toString()));
            }
            query.put("track.bucket", timeRestriction);
        }*/

        final Promise<MeasurementIterator> promise = Promise.promise();
        final var initializedHandler = new Handler<MeasurementIterator>() {
            @Override
            public void handle(final MeasurementIterator output) {
                promise.complete(output);
            }
        };
        final var geolocationStream = dataClient.findBatchWithOptions(DESERIALIZED_COLLECTION_NAME, query,
                strategy.findOptions());
        // FIXME: add sensor data streams if `strategy` is `MeasurementRetrievalWithSensorData`
        new MeasurementIterator(geolocationStream, strategy, promise::fail, initializedHandler);
        return promise.future();
    }
}
