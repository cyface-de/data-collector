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
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = -8238960498171859970L;
    /**
     * The worldwide unique identifier of the measurement, this {@link GeoLocationRecord} belongs to.
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
     * @param measurementIdentifier The worldwide unique identifier of the measurement, this
     *            {@link GeoLocationRecord}
     *            belongs to
     * @param timestamp The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch)
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     */
    @SuppressWarnings("unused") // Part of the API
    public GeoLocationRecord(final MeasurementIdentifier measurementIdentifier, final long timestamp,
                             final double latitude, final double longitude) {
        this(measurementIdentifier, timestamp, latitude, longitude, null);
    }

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param measurementIdentifier The worldwide unique identifier of the measurement, this {@link GeoLocationRecord}
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
     * @return The worldwide unique identifier of the measurement, this {@link GeoLocationRecord} belongs to.
     */
    public final MeasurementIdentifier getMeasurementIdentifier() {
        return measurementIdentifier;
    }

    /**
     * @param measurementIdentifier The worldwide unique identifier of the measurement, this
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
