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
import java.util.Set;

import org.bson.types.ObjectId;

import de.cyface.model.MeasurementIdentifier;
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
     * The name of the collection in the Mongo database used to store deserialized measurements.
     */
    private final String collectionName;
    /**
     * The query which defines which data to load.
     */
    private final JsonObject query;
    /**
     * The {@link MeasurementRetrievalStrategy} to be used to load data from the database.
     */
    private final MeasurementRetrievalStrategy strategy;
    /**
     * The default name of the collection in the Mongo database which stores the deserialized measurement data.
     */
    public static final String DEFAULT_COLLECTION_NAME = "deserialized";

    /**
     * Constructs a fully initialized object of this object.
     *
     * @param collectionName The name of the collection in the Mongo database used to store deserialized measurements.
     * @param strategy the {@link MeasurementRetrievalStrategy} to be used when loading the measurements.
     * @param measurementIdentifiers The identifiers of the measurements to loaded
     */
    public MeasurementRetriever(final String collectionName, final MeasurementRetrievalStrategy strategy,
            final Set<MeasurementIdentifier> measurementIdentifiers) {

        this.collectionName = collectionName;
        this.strategy = strategy;
        final var ids = new JsonArray();
        measurementIdentifiers.forEach(id -> {
            final var subQuery = new JsonObject().put("metaData.deviceId", id.getDeviceIdentifier()).put(
                    "metaData.measurementId", id.getMeasurementIdentifier());
            ids.add(subQuery);
        });
        this.query = new JsonObject().put("$or", ids);
    }

    /**
     * Constructs a fully initialized object of this object.
     *
     * @param collectionName The name of the collection in the Mongo database used to store deserialized measurements.
     * @param strategy the {@link MeasurementRetrievalStrategy} to be used when loading the measurements.
     * @param userIds The ids of the users to load measurements for
     */
    public MeasurementRetriever(final String collectionName, final MeasurementRetrievalStrategy strategy,
            final List<ObjectId> userIds) {

        this(collectionName, strategy, userIds, null, null);
    }

    /**
     * Constructs a fully initialized object of this object.
     *
     * @param collectionName The name of the collection in the Mongo database used to store deserialized measurements.
     * @param strategy the {@link MeasurementRetrievalStrategy} to be used when loading the measurements.
     * @param userIds The ids of the users to load measurements for
     * @param startTime The value is {@code null} or a point in time to only return newer results.
     * @param endTime The value is {@code null} or a point in time to only return older results.
     */
    public MeasurementRetriever(final String collectionName, final MeasurementRetrievalStrategy strategy,
            final List<ObjectId> userIds, final ZonedDateTime startTime, final ZonedDateTime endTime) {

        this.collectionName = collectionName;
        this.strategy = strategy;
        this.query = new JsonObject().put("metaData.userId", new JsonObject().put("$in", new JsonArray(userIds)));
        if (startTime != null || endTime != null) {
            final var timeRestriction = new JsonObject();
            if (startTime != null) {
                timeRestriction.put("$gt", new JsonObject().put("$date", startTime.toString()));
            }
            if (endTime != null) {
                timeRestriction.put("$lt", new JsonObject().put("$date", endTime.toString()));
            }
            query.put("track.bucket", timeRestriction);
        }
    }

    /**
     * Loads all measurements of all users of the specified {@code userNames} from the database.
     *
     * @param dataClient The client to access the data from
     * @return a {@code Future} containing the {@link MeasurementIterator} with the users' data if successful
     */
    @SuppressWarnings("unused") // Part of the API
    public Future<MeasurementIterator> loadMeasurements(final MongoClient dataClient) {

        final Promise<MeasurementIterator> promise = Promise.promise();

        // Fix CY-5944: Or must contain elements
        if (query.containsKey("$or") && query.getJsonArray("$or").size() == 0) {
            promise.fail("At least one measurement need to be loaded but 0 where requested");
        } else {
            final var initializedHandler = new Handler<MeasurementIterator>() {
                @Override
                public void handle(final MeasurementIterator output) {
                    promise.complete(output);
                }
            };
            final var bucketStream = dataClient.findBatchWithOptions(collectionName, query, strategy.findOptions());
            new MeasurementIterator(bucketStream, strategy, promise::fail, initializedHandler);
        }

        return promise.future();
    }
}
