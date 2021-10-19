/*
 * Copyright 2019-2021 Cyface GmbH
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
package de.cyface.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single measurement captured by a Cyface measurement device.
 * <p>
 * Even though this object has setters for all fields and a no argument constructor, it should be handled as immutable.
 * The reason for the existence of those setters and the constructor is the requirement to use objects of this class as
 * part of Apache Flink Pipelines, which require public setters and a no argument constructor to transfer objects
 * between cluster nodes.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 */
public class Measurement implements Serializable {

    /**
     * The logger used by objects of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Measurement.class);
    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = 4195718001652533383L;
    /**
     * The context of this {@code Measurement}.
     */
    private MetaData metaData;
    /**
     * The data collected for this {@code Measurement} in {@code Track}-slices, ordered by timestamp.
     */
    private List<Track> tracks;

    /**
     * Creates a new uninitialized {@code Measurement}. This is only necessary for Flink serialisation and should never
     * be called from your own code.
     */
    public Measurement() {
        this.tracks = Collections.emptyList();
        // Nothing to do here.
    }

    /**
     * Creates a new completely initialized {@code Measurement}.
     *
     * @param metaData The context of this {@code Measurement}.
     * @param tracks The data collected for this {@code Measurement} in {@code Track}-slices, ordered by timestamp.
     */
    public Measurement(final MetaData metaData, final List<Track> tracks) {
        Validate.notNull(metaData);

        this.metaData = metaData;
        this.tracks = new ArrayList<>(tracks);
    }

    /**
     * @return The context of this {@code Measurement}.
     */
    public MetaData getMetaData() {
        return metaData;
    }

    /**
     * @return The data collected for this {@code Measurement} in {@code Track}-slices, ordered by timestamp.
     */
    public List<Track> getTracks() {
        return List.copyOf(tracks);
    }

    /**
     * Required by Apache Flink.
     *
     * @param metaData The context of this {@code Measurement}.
     */
    public void setMetaData(final MetaData metaData) {
        this.metaData = metaData;
    }

    /**
     * Required by Apache Flink.
     *
     * @param tracks The data collected for this {@code Measurement} in {@code Track}-slices, ordered by timestamp.
     */
    public void setTracks(final List<Track> tracks) {
        this.tracks = new ArrayList<>(tracks);
    }

    /**
     * Exports this measurement as a CSV file.
     *
     * @param handler A handler that gets one line of CSV output per call
     * @param withCsvHeader {@code True} if a CSV header should be added before the data
     */
    public void asCsv(final boolean withCsvHeader, final Consumer<String> handler) {
        if (withCsvHeader) {
            csvHeader(handler);
        }

        Modality lastModality = Modality.UNKNOWN;

        // Iterate through tracks
        var modalityTypeDistance = 0.0;
        var totalDistance = 0.0;
        var modalityTypeTravelTime = 0L;
        var totalTravelTime = 0L;
        for (var i = 0; i < tracks.size(); i++) {
            final var trackId = i + 1; // the data receiver probably counts from 1 on not from 0
            final var track = tracks.get(i);

            // Iterate through locations
            RawRecord lastLocation = null;
            for (final var locationRecord : track.getLocationRecords()) {
                if (lastLocation != null) {
                    final var newDistance = lastLocation.distanceTo(locationRecord);
                    modalityTypeDistance += newDistance;
                    totalDistance += newDistance;
                    final var timeTraveled = locationRecord.getTimestamp()
                            - lastLocation.getTimestamp();
                    modalityTypeTravelTime += timeTraveled;
                    totalTravelTime += timeTraveled;
                }

                // Check if the modalityType changed
                if (locationRecord.getModality() != null && locationRecord.getModality() != lastModality) {
                    lastModality = locationRecord.getModality();
                    modalityTypeDistance = 0.0;
                    modalityTypeTravelTime = 0L;
                }

                handler.accept(csvRow(getMetaData(), locationRecord, trackId,
                        modalityTypeDistance, totalDistance,
                        modalityTypeTravelTime, totalTravelTime));

                lastLocation = locationRecord;
            }
        }
    }

    /**
     * Exports this measurement as GeoJSON feature.
     *
     * @param handler A handler that gets the GeoJson feature as string
     */
    public void asGeoJson(final Consumer<String> handler) {
        // We decided to generate a String instead of using a JSON library to avoid dependencies in the model library

        // measurement = geoJson "feature"
        handler.accept("{");
        handler.accept(jsonKeyValue("type", "Feature").stringValue);
        handler.accept(",");

        // All tracks = geometry (MultiLineString)
        handler.accept("\"geometry\":{");
        handler.accept(jsonKeyValue("type", "MultiLineString").stringValue);
        handler.accept(",");
        handler.accept("\"coordinates\":");
        final var tracksCoordinates = convertToLineStringCoordinates(getTracks());
        handler.accept(tracksCoordinates);
        handler.accept("},");

        final var deviceId = jsonKeyValue("deviceId", getMetaData().getIdentifier().getDeviceIdentifier());
        final var measurementId = jsonKeyValue("measurementId",
                getMetaData().getIdentifier().getMeasurementIdentifier());
        final var length = jsonKeyValue("length", getMetaData().getLength());
        final var properties = jsonObject(deviceId, measurementId, length);
        handler.accept(jsonKeyValue("properties", properties).stringValue);

        handler.accept("}");
    }

    /**
     * Exports this measurement as Json <b>without sensor data</b>.
     *
     * @param handler A handler that gets the Json as string
     */
    public void asJson(final Consumer<String> handler) {
        // We decided to generate a String instead of using a JSON library to avoid dependencies in the model library
        handler.accept("{");

        handler.accept(jsonKeyValue("metaData", asJson(metaData)).stringValue);
        handler.accept(",");

        handler.accept("\"tracks\":[");
        tracks.forEach(track -> handler.accept(featureCollection(track).stringValue));
        handler.accept("]");

        handler.accept("}");
    }

    private JsonObject asJson(final MetaData metaData) {
        return jsonObject(jsonKeyValue("username", metaData.getUsername()),
                jsonKeyValue("deviceId", metaData.getIdentifier().getDeviceIdentifier()),
                jsonKeyValue("measurementId", metaData.getIdentifier().getMeasurementIdentifier()),
                jsonKeyValue("length", metaData.getLength()));
    }

    /**
     * Converts a {@link Track} to a {@code GeoJson} "FeatureCollection" with "Point" "Features".
     *
     * @param track the {@code Track} to convert
     * @return the converted {@code Track}
     */
    private JsonObject featureCollection(final Track track) {
        final var points = geoJsonPointFeatures(track.getLocationRecords());
        final var type = jsonKeyValue("type", "FeatureCollection");
        final var features = jsonKeyValue("features", points);
        return jsonObject(type, features);
    }

    private JsonArray geoJsonPointFeatures(final List<RawRecord> list) {
        return jsonArray(list.stream().map(l -> geoJsonPointFeature(l).stringValue).toArray(String[]::new));
    }

    private JsonObject geoJsonPointFeature(final RawRecord record) {
        final var type = jsonKeyValue("type", "Feature");

        final var geometryType = jsonKeyValue("type", "Point");
        final var lat = String.valueOf(record.getLatitude());
        final var lon = String.valueOf(record.getLongitude());
        final var coordinates = jsonKeyValue("coordinates", jsonArray(lon, lat));
        final var geometry = jsonKeyValue("geometry", jsonObject(geometryType, coordinates));

        final var timestamp = jsonKeyValue("timestamp", record.getTimestamp());
        final var speed = jsonKeyValue("speed", record.getSpeed());
        final var accuracy = jsonKeyValue("accuracy", record.getAccuracy());
        final var modality = jsonKeyValue("modality", record.getModality().getDatabaseIdentifier());
        final var properties = jsonKeyValue("properties", jsonObject(timestamp, speed, accuracy, modality));

        return jsonObject(type, geometry, properties);
    }

    private JsonArray jsonArray(final String... objects) {
        final var builder = new StringBuilder("[");
        Arrays.stream(objects).forEach(p -> builder.append(p).append(","));
        builder.deleteCharAt(builder.length() - 1); // remove trailing comma
        builder.append("]");
        return new JsonArray(builder.toString());
    }

    private JsonObject jsonObject(final KeyValuePair... keyValuePairs) {
        final var builder = new StringBuilder("{");
        Arrays.stream(keyValuePairs).forEach(p -> builder.append(p.stringValue).append(","));
        builder.deleteCharAt(builder.length() - 1); // remove trailing comma
        builder.append("}");
        return new JsonObject(builder.toString());
    }

    private KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final JsonArray value) {
        return new KeyValuePair("\"" + key + "\":" + value.stringValue);
    }

    private KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key,
            final JsonObject value) {
        return new KeyValuePair("\"" + key + "\":" + value.stringValue);
    }

    private KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final long value) {
        return new KeyValuePair("\"" + key + "\":" + value);
    }

    private KeyValuePair jsonKeyValue(@SuppressWarnings("SameParameterValue") final String key, final double value) {
        return new KeyValuePair("\"" + key + "\":" + value);
    }

    private KeyValuePair jsonKeyValue(final String key, final String value) {
        return new KeyValuePair("\"" + key + "\":\"" + value + "\"");
    }

    /**
     * Clears the data within this measurement starting at the provided <code>timestamp</code> in milliseconds since the
     * 01.01.1970 (UNIX Epoch).
     * <p>
     * This call modifies the called measurement.
     *
     * @param timestamp The timestamp in milliseconds since the first of January 1970 to begin clearing the data at
     * @return This cleared <code>Measurement</code>
     * @throws TimestampNotFound If the timestamp is not within the timeframe of this measurement
     */
    public Measurement clearAfter(final long timestamp) throws TimestampNotFound {
        final var trackIndex = getIndexOfTrackContaining(timestamp);
        while (tracks.size() - 1 > trackIndex) {
            tracks.remove(tracks.size() - 1);
        }
        tracks.get(trackIndex).clearAfter(timestamp);
        return this;
    }

    /**
     * Tries to find the track from this measurement containing the provided timestamp.
     *
     * @param timestamp A timestamp in milliseconds since the first of January 1970
     * @return The index of the track containing the provided timestamp
     * @throws TimestampNotFound If the timestamp is not within the timeframe of this measurement
     */
    private int getIndexOfTrackContaining(final long timestamp) throws TimestampNotFound {
        LOGGER.trace("Getting track index for timestamp: {} ({})!",
                SimpleDateFormat.getDateTimeInstance().format(new Date(timestamp)), timestamp);
        for (var i = 0; i < tracks.size(); i++) {
            var track = tracks.get(i);
            var minRotationsTimestamp = track.getRotations().isEmpty() ? Long.MAX_VALUE
                    : track.getRotations().get(0).getTimestamp();
            var minDirectionsTimestamp = track.getDirections().isEmpty() ? Long.MAX_VALUE
                    : track.getDirections().get(0).getTimestamp();
            var minAccelerationsTimestamp = track.getAccelerations().isEmpty() ? Long.MAX_VALUE
                    : track.getAccelerations().get(0).getTimestamp();
            var minLocationsTimestamp = track.getLocationRecords().isEmpty() ? Long.MAX_VALUE
                    : track.getLocationRecords().get(0).getTimestamp();
            var minTrackTimestamp = Math.min(minRotationsTimestamp,
                    Math.min(minDirectionsTimestamp, Math.min(minAccelerationsTimestamp, minLocationsTimestamp)));
            Validate.isTrue(minTrackTimestamp < Long.MAX_VALUE);

            var maxRotationsTimestamp = track.getRotations().isEmpty() ? Long.MIN_VALUE
                    : track.getRotations().get(track.getRotations().size() - 1).getTimestamp();
            var maxDirectionsTimestamp = track.getDirections().isEmpty() ? Long.MIN_VALUE
                    : track.getDirections().get(track.getDirections().size() - 1).getTimestamp();
            var maxAccelerationsTimestamp = track.getAccelerations().isEmpty() ? Long.MIN_VALUE
                    : track.getAccelerations().get(track.getAccelerations().size() - 1).getTimestamp();
            var maxLocationsTimestamp = track.getLocationRecords().isEmpty() ? Long.MIN_VALUE
                    : track.getLocationRecords().get(track.getLocationRecords().size() - 1).getTimestamp();
            var maxTrackTimestamp = Math.max(maxRotationsTimestamp,
                    Math.max(maxDirectionsTimestamp, Math.max(maxAccelerationsTimestamp, maxLocationsTimestamp)));
            Validate.isTrue(maxTrackTimestamp > Long.MIN_VALUE);

            LOGGER.trace("Min timestamp for index {} is {} ({}).", i,
                    SimpleDateFormat.getDateTimeInstance().format(new Date(minTrackTimestamp)), minTrackTimestamp);
            LOGGER.trace("Max timestamp for index {} is {} ({}).", i,
                    SimpleDateFormat.getDateTimeInstance().format(new Date(maxTrackTimestamp)), maxTrackTimestamp);
            if (timestamp >= minTrackTimestamp && timestamp <= maxTrackTimestamp) {
                LOGGER.trace("Selected index {}.", i);
                return i;
            }
        }
        throw new TimestampNotFound(String.format("Unable to find track index for timestamp %s (%s) in measurement %s!",
                SimpleDateFormat.getDateTimeInstance().format(new Date(timestamp)), timestamp,
                getMetaData()));
    }

    /**
     * Creates a CSV header for this measurement.
     *
     * @param handler The handler that is notified of the new CSV row.
     */
    public static void csvHeader(final Consumer<String> handler) {
        final var csvHeaderRow = String.join(",", "username", "deviceId",
                "measurementId",
                "trackId",
                "timestamp [ms]", "latitude", "longitude", "speed [m/s]",
                "accuracy [m]",
                "modalityType", "modalityTypeDistance [km]", "distance [km]",
                "modalityTypeTravelTime [ms]", "travelTime [ms]");
        handler.accept(csvHeaderRow);
    }

    /**
     * Converts one location entry annotated with meta data to a CSV row.
     *
     * @param metaData the {@code Measurement} of the {@param location}
     * @param locationRecord the {@code GeoLocationRecord} to be processed
     * @param trackId the id of the sub track starting at 1
     * @param modalityTypeDistance the distance traveled so far with this {@param modality} type
     * @param totalDistance the total distance traveled so far
     * @param modalityTypeTravelTime the time traveled so far with this {@param modality} type
     * @param totalTravelTime the time traveled so far
     * @return the csv row as String
     */
    private String csvRow(final MetaData metaData, final RawRecord locationRecord, final int trackId,
            final double modalityTypeDistance, final double totalDistance,
            final long modalityTypeTravelTime, final long totalTravelTime) {
        final var username = metaData.getUsername();
        final var deviceId = metaData.getIdentifier().getDeviceIdentifier();
        final var measurementId = String.valueOf(metaData.getIdentifier().getMeasurementIdentifier());
        return String.join(",", username, deviceId, measurementId, String.valueOf(trackId),
                String.valueOf(locationRecord.getTimestamp()),
                String.valueOf(locationRecord.getLatitude()),
                String.valueOf(locationRecord.getLongitude()), String.valueOf(locationRecord.getSpeed()),
                String.valueOf(locationRecord.getAccuracy()),
                locationRecord.getModality().getDatabaseIdentifier(),
                String.valueOf(modalityTypeDistance), String.valueOf(totalDistance),
                String.valueOf(modalityTypeTravelTime), String.valueOf(totalTravelTime));
    }

    /**
     * Converts a single track to geoJson "coordinates".
     *
     * @param tracks the {@code Track}s to be processed
     * @return the string representation of the geoJson coordinates
     */
    private String convertToLineStringCoordinates(final List<Track> tracks) {
        final var builder = new StringBuilder("[");

        // Each track is a LineString
        tracks.forEach(track -> {
            final var points = jsonArray(
                    track.getLocationRecords().stream().map(l -> geoJsonCoordinates(l).stringValue)
                            .toArray(String[]::new));
            builder.append(points.stringValue);
            builder.append(",");
        });
        builder.deleteCharAt(builder.length() - 1); // delete last ","
        builder.append("]");

        return builder.toString();
    }

    private JsonArray geoJsonCoordinates(final RawRecord record) {
        return jsonArray(String.valueOf(record.getLongitude()), String.valueOf(record.getLatitude()));
    }

    @Override
    public String toString() {
        return "Measurement{" +
                "metaData=" + metaData +
                ", tracks=" + tracks +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        final var that = (Measurement)o;
        return metaData.equals(that.metaData) &&
                tracks.equals(that.tracks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaData, tracks);
    }

    private static class KeyValuePair {
        private final String stringValue;

        public KeyValuePair(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getStringValue() {
            return stringValue;
        }
    }

    private static class JsonObject {
        private final String stringValue;

        public JsonObject(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getStringValue() {
            return stringValue;
        }
    }

    private static class JsonArray {
        private final String stringValue;

        public JsonArray(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getStringValue() {
            return stringValue;
        }
    }
}
