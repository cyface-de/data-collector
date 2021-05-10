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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;

import de.cyface.model.Measurement;
import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.MetaData;
import de.cyface.model.Modality;
import de.cyface.model.Point3D;
import de.cyface.model.RawRecord;
import de.cyface.model.Track;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * Loads deserialized measurements from the database for a vert.x context.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.2.0
 */
public class MeasurementRetriever {

    /**
     * The name of the collection in the Mongo database which stores the deserialized measurement data.
     * TODO: should be a configurable from outside (parameter)
     */
    private static final String DESERIALIZED_COLLECTION_NAME = "deserialized";

    /**
     * Loads all measurements of all users of the specified {@code userNames} from the database.
     *
     * @param userNames The names of the users of whom all measurements are to be loaded
     * @param dataClient The client to access the data from
     * @return a {@code Future} containing the users' {@code Measurement}s if successful
     */
    public Future<List<Measurement>> loadMeasurements(final List<String> userNames, final MongoClient dataClient) {
        return loadMeasurements(userNames, dataClient, null, null);
    }

    /**
     * Loads all measurements of all users of the specified {@code userNames} from the database.
     *
     * @param userNames The names of the users of whom all measurements are to be loaded
     * @param dataClient The client to access the data from
     * @param startTime The value is {@code null} or a point in time to only return newer results.
     * @param endTime The value is {@code null} or a point in time to only return older results.
     * @return a {@code Future} containing the users' {@code Measurement}s if successful
     */
    public Future<List<Measurement>> loadMeasurements(final List<String> userNames, final MongoClient dataClient,
            final ZonedDateTime startTime, final ZonedDateTime endTime) {

        final Promise<List<Measurement>> promise = Promise.promise();
        final Future<List<Measurement>> future = promise.future();

        final var query = new JsonObject();
        query.put("metaData.username", new JsonObject().put("$in", new JsonArray(userNames)));
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

        dataClient.find(DESERIALIZED_COLLECTION_NAME, query, mongoResult -> {
            if (mongoResult.succeeded()) {
                final var measurements = pojo(mongoResult.result());
                promise.complete(measurements);
            } else {
                promise.fail(mongoResult.cause());
            }
        });

        return future;
    }

    /**
     * Returns the specified {@code measurementMongoData} list as POJO list.
     *
     * @param trackBuckets the `buckets` containing track slices of all measurements of a group of users.
     * @return the list of {@link Measurement}s.
     */
    List<Measurement> pojo(final List<JsonObject> trackBuckets) {
        if (trackBuckets.size() == 0) {
            return new ArrayList<>();
        }

        // Convert documents
        final var buckets = trackBuckets(trackBuckets);

        // Group buckets by measurement
        final var groupedBuckets = buckets.stream().collect(groupingBy(TrackBucket::getMetaData));

        // Create a Measurement from a bucket group
        final List<Measurement> measurements = new ArrayList<>();
        groupedBuckets.forEach((metaData, bucketGroup) -> {
            final Measurement measurement = measurement(metaData, bucketGroup);
            measurements.add(measurement);
        });

        return measurements;
    }

    List<TrackBucket> trackBuckets(List<JsonObject> trackBuckets) {

        final var buckets = new ArrayList<TrackBucket>();
        trackBuckets.forEach(trackBucket -> {
            try {
                final var bucket = trackBucket(trackBucket);
                buckets.add(bucket);
            } catch (ParseException e) {
                throw new IllegalArgumentException(e);
            }
        });
        return buckets;
    }

    /**
     * Constructs a new measurement from a document loaded from the database.
     *
     * @param metaData the {@link MetaData} collected for that measurement
     * @param trackBuckets The data to load the measurement from
     * @return The newly created {@link Measurement}
     */
    private Measurement measurement(final MetaData metaData, final List<TrackBucket> trackBuckets) {

        final var tracks = tracks(trackBuckets);
        return new Measurement(metaData, tracks);
    }

    /**
     * Merges {@link TrackBucket}s into {@link Track}s.
     *
     * @param trackBuckets the data to merge
     * @return the tracks
     */
    List<Track> tracks(final List<TrackBucket> trackBuckets) {

        // Group by trackId
        final var groupedBuckets = trackBuckets.stream().collect(groupingBy(TrackBucket::getTrackId));

        // Sort bucket groups by trackId
        final var sortedBucketGroups = groupedBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));

        // Convert buckets to Track
        final var tracks = new ArrayList<Track>();
        sortedBucketGroups.forEach((trackId, bucketGroup) -> {
            // Sort buckets
            final var sortedBuckets = bucketGroup.stream().sorted(Comparator.comparing(TrackBucket::getBucket))
                    .collect(toList());

            // Merge buckets
            final var locations = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getLocationRecords().stream())
                    .collect(Collectors.toList());
            final var accelerations = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getAccelerations().stream())
                    .collect(Collectors.toList());
            final var rotations = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getRotations().stream())
                    .collect(Collectors.toList());
            final var directions = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getDirections().stream())
                    .collect(Collectors.toList());

            final var track = new Track(locations, accelerations, rotations, directions);
            tracks.add(track);
        });
        return tracks;
    }

    /**
     * Returns the {@code MetaData} as POJO.
     *
     * @param document the {@code Document} containing the measurement, {@link MetaData#getVersion()} 1.0.0.
     * @return the metadata POJO
     */
    private MetaData metaData(final JsonObject document) {
        final JsonObject metaData = document.getJsonObject("metaData");
        final String version = metaData.getString("version");
        Validate.isTrue(version.equals(MetaData.CURRENT_VERSION),
                "Encountered data in invalid format. Only Cyface Format Version 1.0.0 is supported!");

        final MeasurementIdentifier identifier = new MeasurementIdentifier(metaData.getString("deviceId"),
                metaData.getLong("measurementId"));
        final String deviceType = metaData.getString("deviceType");
        final String osVersion = metaData.getString("osVersion");
        final String appVersion = metaData.getString("appVersion");
        final double length = metaData.getDouble("length");
        final String username = metaData.getString("username");
        return new MetaData(identifier, deviceType, osVersion, appVersion, length, username, version);
    }

    /**
     * Returns the {@code Track} list as POJO.
     *
     * @param document the {@code Document} containing the measurement, {@link MetaData#getVersion()} 1.0.0.
     * @return the track list POJO
     * @throws ParseException if the track bucket date cannot be parsed
     */
    private TrackBucket trackBucket(final JsonObject document)
            throws ParseException {
        final var metaData = metaData(document);
        // Avoiding to have a Track constructor from Document to avoid mongodb dependency in model library
        final var trackDocument = document.getJsonObject("track");
        final var trackId = trackDocument.getInteger("trackId");
        final var bucket = trackDocument.getJsonObject("bucket");
        final var geoLocationsDocuments = trackDocument.getJsonArray("geoLocations");
        final var accelerationsDocuments = trackDocument.getJsonArray("accelerations");
        final var rotationsDocuments = trackDocument.getJsonArray("rotations");
        final var directionsDocuments = trackDocument.getJsonArray("directions");
        final var locationRecords = geoLocations(geoLocationsDocuments, metaData.getIdentifier());
        final var accelerations = point3D(accelerationsDocuments);
        final var rotations = point3D(rotationsDocuments);
        final var directions = point3D(directionsDocuments);
        final var track = new Track(locationRecords, accelerations, rotations, directions);
        return new TrackBucket(trackId, bucket, track, metaData);
    }

    /**
     * Returns the {@code GeoLocationRecord} list as POJO.
     *
     * @param documents the {@code Document} list containing the {@code GeoLocation}s in {@link MetaData#getVersion()}
     *            1.0.0.
     * @param identifier the identifier of the measurement of this track
     * @return the record list POJO
     */
    private List<RawRecord> geoLocations(final JsonArray documents, final MeasurementIdentifier identifier) {
        final var records = new ArrayList<RawRecord>();
        for (int i = 0; i < documents.size(); i++) {
            final var doc = documents.getJsonObject(i);
            final var timestamp = doc.getLong("timestamp");
            final var latitude = doc.getDouble("latitude");
            final var longitude = doc.getDouble("longitude");
            final var elevation = doc.getDouble("elevation");
            final var speed = doc.getDouble("speed");
            final var accuracy = doc.getDouble("accuracy");
            final var modality = Modality.valueOf(doc.getString("modality"));
            Validate.notNull(modality, "Unable to identify modality type: " + doc.getString("modality"));
            final var record = new RawRecord(identifier, timestamp, latitude, longitude, elevation, accuracy, speed,
                    modality);
            records.add(record);
        }
        return records;
    }

    /**
     * Returns the {@code Point3D} list as POJO.
     *
     * @param documents the {@code Document} list containing the {@code Point3D}s in {@link MetaData#getVersion()}
     *            1.0.0.
     * @return the point list POJO
     */
    private List<Point3D> point3D(final JsonArray documents) {
        final List<Point3D> point3DS = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            final JsonObject doc = documents.getJsonObject(i);
            final long timestamp = doc.getLong("timestamp");
            // MongoDB stores all numbers in the same data type
            final float x = doc.getDouble("x").floatValue();
            final float y = doc.getDouble("y").floatValue();
            final float z = doc.getDouble("z").floatValue();
            final Point3D point3D = new Point3D(x, y, z, timestamp);
            point3DS.add(point3D);
        }
        return point3DS;
    }

    static class TrackBucket {
        final int trackId;
        final Date bucket;
        final Track track;
        final MetaData metaData;

        public TrackBucket(int trackId, JsonObject bucket, Track track, MetaData metaData) throws ParseException {
            // noinspection SpellCheckingInspection
            this(trackId, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(bucket.getString("$date")), track,
                    metaData);
        }

        public TrackBucket(int trackId, Date bucket, Track track, MetaData metaData) {
            this.trackId = trackId;
            this.bucket = bucket;
            this.track = track;
            this.metaData = metaData;
        }

        public int getTrackId() {
            return trackId;
        }

        public Date getBucket() {
            return bucket;
        }

        public Track getTrack() {
            return track;
        }

        public MetaData getMetaData() {
            return metaData;
        }
    }
}
