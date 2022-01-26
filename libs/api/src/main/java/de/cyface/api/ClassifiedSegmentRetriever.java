/*
 * Copyright 2021 Cyface GmbH
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

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.cyface.api.model.ClassifiedSegment;
import de.cyface.api.model.Geometry;
import de.cyface.model.Modality;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Loads classified segments from the database for a vert.x context.
 *
 * @author Armin Schnabel
 */
@SuppressWarnings("unused") // Part of the API
public class ClassifiedSegmentRetriever {

    /**
     * The name of the collection in the Mongo database used to store classified segments data.
     */
    private final String collectionName;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param collectionName The name of the collection in the Mongo database used to store classified segments.
     */
    public ClassifiedSegmentRetriever(final String collectionName) {
        this.collectionName = collectionName;
    }

    /**
     * Loads all classified segments of all users of the specified {@code userNames} from the database.
     *
     * @param userNames The names of the users to load data for.
     * @param dataClient The client to access the data from.
     * @param startTime The value is {@code null} or a point in time to only return newer results.
     * @param endTime The value is {@code null} or a point in time to only return older results.
     * @return a {@code Future} containing the users' data if successful.
     */
    public Future<List<ClassifiedSegment>> loadSegments(final List<String> userNames, final MongoClient dataClient,
                                                        final ZonedDateTime startTime, final ZonedDateTime endTime) {

        final Promise<List<ClassifiedSegment>> promise = Promise.promise();
        final Future<List<ClassifiedSegment>> future = promise.future();

        final var query = new JsonObject();
        query.put("username", new JsonObject().put("$in", new JsonArray(userNames)));
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

        dataClient.find(collectionName, query, mongoResult -> {
            if (mongoResult.succeeded()) {
                final var segments = pojo(mongoResult.result());
                promise.complete(segments);
            } else {
                promise.fail(mongoResult.cause());
            }
        });

        return future;
    }

    /**
     * Returns classified segments as a POJO list.
     *
     * @param segments classified segments of a group of users from the database.
     * @return the classified segments as POJO list.
     */
    private List<ClassifiedSegment> pojo(final List<JsonObject> segments) {
        if (segments.size() == 0) {
            return new ArrayList<>();
        }

        // Convert documents
        final var pojoSegments = segments.stream().map(this::toSegment);
        return pojoSegments.collect(Collectors.toList());
    }

    /**
     * Constructs a fully initialized {@link ClassifiedSegment} from a database entry.
     *
     * @param segment The entry from the database.
     */
    private ClassifiedSegment toSegment(final JsonObject segment) {
        final var oid = segment.getJsonObject("_id").getString("$oid");
        final var forward = segment.getBoolean("forward");
        final var geometry = segment.getJsonObject("geometry");
        final var length = segment.getDouble("length");
        final var modality = Modality.valueOf(segment.getString("modality"));
        final var vnk = segment.getLong("vnk");
        final var nnk = segment.getLong("nnk");
        final var start = segment.getInteger("start");
        final var latestDataPoint = OffsetDateTime.parse(segment.getJsonObject("latest_data_point").getString("$date"));
        final var username = segment.getString("username");
        final var expectedValue = segment.getDouble("expected_value");
        final var variance = segment.getDouble("variance");
        // We're expecting a large number of segments stored, we store the quality class as Integer instead of String.
        // This should reduce the collection size (at least when uncompressed).
        // Later on we might store this as a normalized value between zero and one.
        final var quality = ClassifiedSegment.SurfaceQuality.valueOf(segment.getInteger("quality"));
        final var dataPointCount = segment.getLong("data_point_count");
        final var version = segment.getString("version");
        return new ClassifiedSegment(oid, forward, toGeometry(geometry), length, modality, vnk, nnk, start, latestDataPoint, username, expectedValue, variance, quality, dataPointCount, version);
    }

    /**
     * Constructs a fully initialized {@link Geometry} from a database entry.
     *
     * @param geometry The entry from the database.
     */
    private Geometry toGeometry(final JsonObject geometry) {
        final var type = geometry.getString("type");
        final var array = geometry.getJsonArray("coordinates");
        final var coordinates = new ArrayList<Geometry.Coordinate>();
        array.forEach(coordinate -> {
            final var c = (JsonArray)coordinate;
            coordinates.add(new Geometry.Coordinate(c.getDouble(1), c.getDouble(0)));
        });
        return new Geometry(type, coordinates.toArray(new Geometry.Coordinate[0]));
    }
}
