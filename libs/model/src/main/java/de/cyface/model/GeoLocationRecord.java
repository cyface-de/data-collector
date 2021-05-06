/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.model;

import java.util.Objects;

import org.apache.commons.lang3.Validate;

/**
 * A model POJO representing the recording of a {@link GeoLocation}.
 *
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.0.0
 */
public abstract class GeoLocationRecord extends GeoLocation implements DataPoint {
    /**
     * The world wide unique identifier of the measurement, this {@link GeoLocationRecord} belongs to.
     */
    private MeasurementIdentifier measurementIdentifier;
    /**
     * The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch)
     */
    private long timestamp;

    /**
     * No argument constructor as required by Apache Flink. Do NOT use this in your own code.
     */
    public GeoLocationRecord() {
        // Nothing to do here.
    }

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param measurementIdentifier The world wide unique identifier of the measurement, this
     *            {@link GeoLocationRecord}
     *            belongs to
     * @param timestamp The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch)
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     */
    public GeoLocationRecord(final MeasurementIdentifier measurementIdentifier, final long timestamp,
            final double latitude, final double longitude) {
        this(measurementIdentifier, timestamp, latitude, longitude, null);
    }

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param measurementIdentifier The world wide unique identifier of the measurement, this {@link GeoLocationRecord}
     *            belongs to
     * @param timestamp The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch)
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     * @param elevation The elevation above sea level in meters or <code>null</code> if it could not be calculated
     */
    public GeoLocationRecord(final MeasurementIdentifier measurementIdentifier, final long timestamp,
            final double latitude, final double longitude, final Double elevation) {
        super(latitude, longitude, elevation);
        setTimestamp(timestamp);
        setMeasurementIdentifier(measurementIdentifier);
    }

    /**
     * @return The world wide unique identifier of the measurement, this {@link GeoLocationRecord} belongs to.
     */
    public final MeasurementIdentifier getMeasurementIdentifier() {
        return measurementIdentifier;
    }

    /**
     * @param measurementIdentifier The world wide unique identifier of the measurement, this
     *            {@link GeoLocationRecord}
     *            belongs to.
     */
    public final void setMeasurementIdentifier(final MeasurementIdentifier measurementIdentifier) {
        this.measurementIdentifier = measurementIdentifier;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        Validate.isTrue(timestamp > 0L);
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(final DataPoint that) {
        return Long.compare(this.timestamp, that.getTimestamp());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        GeoLocationRecord that = (GeoLocationRecord)o;
        return timestamp == that.timestamp && Objects.equals(measurementIdentifier, that.measurementIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), measurementIdentifier, timestamp);
    }

    @Override
    public String toString() {
        return "GeoLocationRecord{" +
                "measurementIdentifier=" + measurementIdentifier +
                ", timestamp=" + timestamp +
                '}';
    }
}
