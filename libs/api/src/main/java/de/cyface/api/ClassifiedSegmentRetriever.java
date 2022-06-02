/*
 * Copyright 2021 - 2022 Cyface GmbH
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

import static de.cyface.api.model.ClassifiedSegmentFactoryProvider.Mode.MEASUREMENT_BASED;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.cyface.api.model.ClassifiedMeasurementSegment;
import de.cyface.api.model.ClassifiedSegment;
import de.cyface.api.model.ClassifiedSegmentFactory;
import de.cyface.api.model.ClassifiedSegmentFactoryProvider;
import de.cyface.api.model.User;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Loads classified segments from the database for a vert.x context.
 *
 * @author Armin Schnabel
 * @since 6.1.0
 * @version 1.0.0
 */
@SuppressWarnings("unused") // Part of the API
public class ClassifiedSegmentRetriever<T extends ClassifiedSegment> {

    /**
     * The field name of the database field which contains the user id.
     */
    public static final String USER_ID_FIELD = "userId";
    /**
     * The name of the collection in the Mongo database used to store classified segments data.
     */
    private final String collectionName;
    /**
     * The query which defines which data to load.
     */
    private final JsonObject query;
    /**
     * The factory to be used to create new instances retrieved by this class.
     */
    private final ClassifiedSegmentFactory<?> factory;

    /**
     * Constructs a fully initialized retriever to load segments by id.
     *
     * @param collectionName The name of the collection in the Mongo database used to store deserialized measurements.
     * @param factory The factory to be used to create new instances retrieved by this class.
     * @param segmentIds The identifiers of the segments to loaded
     */
    public ClassifiedSegmentRetriever(final String collectionName, final ClassifiedSegmentFactory<?> factory,
            final Set<String> segmentIds) {

        this.collectionName = collectionName;
        this.factory = factory;

        final var ids = new JsonArray();
        segmentIds.forEach(id -> ids.add(new JsonObject().put("_id", new JsonObject().put("$oid", id))));
        this.query = new JsonObject().put("$or", ids);
    }

    /**
     * Constructs a fully initialized retriever to load all segments of a list of users.
     *
     * @param collectionName The name of the collection in the Mongo database used to store classified segments.
     * @param factory The factory to be used to create new instances retrieved by this class.
     * @param users The users to load data for.
     */
    public ClassifiedSegmentRetriever(final String collectionName, final ClassifiedSegmentFactory<?> factory,
            final List<User> users) {
        this(collectionName, factory, users, null, null);
    }

    /**
     * Constructs a fully initialized retriever to load all segments of a list of users for a specific time frame.
     *
     * @param collectionName The name of the collection in the Mongo database used to store classified segments.
     * @param factory The factory to be used to create new instances retrieved by this class.
     * @param users The users to load data for.
     * @param startTime The value is {@code null} or a point in time to only return newer results.
     * @param endTime The value is {@code null} or a point in time to only return older results.
     */
    public ClassifiedSegmentRetriever(final String collectionName, final ClassifiedSegmentFactory<?> factory,
            final List<User> users, final ZonedDateTime startTime, final ZonedDateTime endTime) {
        this.collectionName = collectionName;
        this.factory = factory;

        this.query = new JsonObject();
        final var userIds = users.stream().map(u -> new JsonObject().put("$oid", u.getIdString()))
                .collect(Collectors.toList());
        query.put(USER_ID_FIELD, new JsonObject().put("$in", new JsonArray(userIds)));
        if (startTime != null || endTime != null) {
            final var timeRestriction = new JsonObject();
            if (startTime != null) {
                timeRestriction.put("$gt", startTime.toInstant().toEpochMilli());
            }
            if (endTime != null) {
                timeRestriction.put("$lt", endTime.toInstant().toEpochMilli());
            }
            query.put("latest_data_point", timeRestriction);
        }
    }

    /**
     * Constructs a fully initialized retriever which to load all {@link ClassifiedMeasurementSegment}s ("history") of a
     * {@link ClassifiedSegment}.
     *
     * @param collectionName The name of the collection in the Mongo database used to store deserialized measurements.
     * @param segment The {@link ClassifiedSegment} to load all {@link ClassifiedMeasurementSegment}s for
     */
    public ClassifiedSegmentRetriever(final String collectionName, final ClassifiedSegment segment) {

        this.collectionName = collectionName;
        this.factory = ClassifiedSegmentFactoryProvider.getFactory(MEASUREMENT_BASED);

        this.query = new JsonObject()
                .put("userId", segment.getUserId())
                .put("modality", segment.getModality().getDatabaseIdentifier())
                .put("way", segment.getWay())
                .put("forward", segment.isForward())
                .put("way_offset", segment.getWayOffset());
    }

    /**
     * Loads the segment defined via the constructor of this class from the database.
     *
     * @param mongoClient The client to access the data from.
     * @return a {@code Future} containing the users' data if successful.
     */
    public Future<List<? extends ClassifiedSegment>> loadSegments(final MongoClient mongoClient) {
        final Promise<List<? extends ClassifiedSegment>> promise = Promise.promise();

        // Fix CY-5944: Or must contain elements
        if (query.containsKey("$or") && query.getJsonArray("$or").size() == 0) {
            promise.fail("At least one segment need to be loaded but 0 where requested");
        } else {
            final var find = mongoClient.find(collectionName, query);
            find.onSuccess(result -> {
                try {
                    promise.complete(pojo(result));
                } catch (RuntimeException e) {
                    promise.fail(e);
                }
            });
            find.onFailure(promise::fail);
        }

        return promise.future();
    }

    /**
     * Returns classified segments as a POJO list.
     *
     * @param segments classified segments of a group of users from the database.
     * @return the classified segments as POJO list.
     */
    private List<? extends ClassifiedSegment> pojo(final List<JsonObject> segments) {
        if (segments.size() == 0) {
            return new ArrayList<>();
        }

        // Convert documents
        final var pojoSegments = segments.stream().map(factory::build);

        return pojoSegments.collect(Collectors.toList());
    }
}
