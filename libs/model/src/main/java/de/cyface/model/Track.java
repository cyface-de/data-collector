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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A part of a measurement for which continuous data is available and ordered by time.
 * <p>
 * A {@code Track} begins with the first {@code GeoLocation} collected after start or resume was triggered during data
 * collection. It stops with the last collected {@code GeoLocation} before the next resume command was triggered or
 * when the very last locations is reached.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
public class Track {
    /**
     * The list of {@code GeoLocationRecord}s collected for this {@code Track} ordered by timestamp.
     */
    private List<RawRecord> locationRecords;
    /**
     * The list of accelerations for this {@code Track} ordered by timestamp. Unit: m/s².
     */
    private List<Point3D> accelerations;
    /**
     * The list of rotations for this {@code Track} ordered by timestamp. Unit: rad/s.
     */
    private List<Point3D> rotations;
    /**
     * The list of directions for this {@code Track} ordered by timestamp. Unit. micro-Tesla (uT).
     */
    private List<Point3D> directions;

    /**
     * Creates a new completely initialized {@code Track}.
     *
     * @param locationRecords The list of {@code RawRecord}s collected for this {@code Track} ordered by
     *            timestamp.
     * @param accelerations The list of accelerations for this {@code Track} ordered by timestamp. Unit: m/s².
     * @param rotations The list of rotations for this {@code Track} ordered by timestamp. Unit: rad/s.
     * @param directions The list of directions for this {@code Track} ordered by timestamp. Unit. micro-Tesla (uT).
     */
    public Track(final List<RawRecord> locationRecords, final List<Point3D> accelerations,
            final List<Point3D> rotations, final List<Point3D> directions) {
        Objects.requireNonNull(locationRecords);
        Objects.requireNonNull(accelerations);
        Objects.requireNonNull(rotations);
        Objects.requireNonNull(directions);

        this.locationRecords = new ArrayList<>(locationRecords);
        this.accelerations = new ArrayList<>(accelerations);
        this.rotations = new ArrayList<>(rotations);
        this.directions = new ArrayList<>(directions);
    }

    /**
     * No argument constructor as required by Apache Flink. Do not use this in your own code.
     */
    public Track() {
        // Nothing to do
    }

    /**
     * @return The list of {@code GeoLocationRecord}s collected for this {@code Track} ordered by timestamp.
     */
    public List<RawRecord> getLocationRecords() {
        return Collections.unmodifiableList(locationRecords);
    }

    /**
     * @return The list of accelerations for this {@code Track} ordered by timestamp. Unit: m/s².
     */
    public List<Point3D> getAccelerations() {
        return Collections.unmodifiableList(accelerations);
    }

    /**
     * @return The list of accelerations for this {@code Track} ordered by timestamp. Unit: m/s².
     */
    public List<Point3D> getRotations() {
        return Collections.unmodifiableList(rotations);
    }

    /**
     * @return The list of directions for this {@code Track} ordered by timestamp. Unit. micro-Tesla (uT).
     */
    public List<Point3D> getDirections() {
        return Collections.unmodifiableList(directions);
    }

    /**
     * Required by Apache Flink.
     *
     * @param locationRecords The list of {@code GeoLocationRecord}s collected for this {@code Track} ordered by
     *            timestamp.
     */
    public void setLocationRecords(final List<RawRecord> locationRecords) {
        Objects.requireNonNull(locationRecords);

        this.locationRecords = new ArrayList<>(locationRecords);
    }

    /**
     * Required by Apache Flink.
     *
     * @param accelerations The list of accelerations for this {@code Track} ordered by timestamp. Unit: m/s².
     */
    public void setAccelerations(final List<Point3D> accelerations) {
        Objects.requireNonNull(locationRecords);

        this.accelerations = new ArrayList<>(accelerations);
    }

    /**
     * Required by Apache Flink.
     *
     * @param rotations The list of accelerations for this {@code Track} ordered by timestamp. Unit: m/s².
     */
    public void setRotations(final List<Point3D> rotations) {
        Objects.requireNonNull(locationRecords);

        this.rotations = new ArrayList<>(rotations);
    }

    /**
     * Required by Apache Flink.
     *
     * @param directions The list of directions for this {@code Track} ordered by timestamp. Unit. micro-Tesla (uT).
     */
    public void setDirections(final List<Point3D> directions) {
        Objects.requireNonNull(locationRecords);

        this.directions = new ArrayList<>(directions);
    }

    /**
     * Removes all data after the provided <code>timestamp</code> from this <code>Track</code>.
     *
     * @param timestamp A UNIX timestamp in milliseconds since the first of January 1970
     * @return This track for method chaining
     */
    public Track clearAfter(final long timestamp) {
        locationRecords = locationRecords.stream().filter(record -> record.getTimestamp() >= timestamp)
                .collect(Collectors.toList());
        accelerations = accelerations.stream().filter(acceleration -> acceleration.getTimestamp() > timestamp)
                .collect(Collectors.toList());
        rotations = rotations.stream().filter(rotation -> rotation.getTimestamp() > timestamp)
                .collect(Collectors.toList());
        directions = directions.stream().filter(direction -> direction.getTimestamp() > timestamp)
                .collect(Collectors.toList());
        return this;
    }

    @Override
    public String toString() {
        return "Track{" +
                "geoLocations=" + locationRecords +
                ", accelerations=" + accelerations +
                ", rotations=" + rotations +
                ", directions=" + directions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Track track = (Track)o;
        return locationRecords.equals(track.locationRecords) &&
                accelerations.equals(track.accelerations) &&
                rotations.equals(track.rotations) &&
                directions.equals(track.directions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationRecords, accelerations, rotations, directions);
    }
}
