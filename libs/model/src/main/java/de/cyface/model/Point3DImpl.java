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
 * A measured {@code DataPoint} with three coordinates such as an acceleration-, rotation- or magnetic value point.
 *
 * @author Klemens Muthmann
 * @version 4.0.1
 * @since 1.0.0
 */
final public class Point3DImpl implements DataPoint, Point3D {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = -4763645418736836160L;
    /**
     * The Unix timestamp at which this point was measured in milliseconds.
     */
    private long timestamp;
    /**
     * The x component of the data point.
     */
    private float x;
    /**
     * The y component of the data point.
     */
    private float y;
    /**
     * The z component of the data point.
     */
    private float z;

    /**
     * Default constructor as required by Apache Flink. Do not use this in your own code.
     */
    @SuppressWarnings("unused")
    public Point3DImpl() {
        // Nothing to do here
    }

    /**
     * Creates a new completely initialized <code>Point3D</code>.
     *
     * @param x The x component of the data point
     * @param y The y component of the data point
     * @param z The z component of the data point
     * @param timestamp The time when this point was measured in milliseconds since 1.1.1970
     */
    public Point3DImpl(final float x, final float y, final float z, final long timestamp) {
        setTimestamp(timestamp);
        setX(x);
        setY(y);
        setZ(z);
    }

    /**
     * Creates a new completely initialized <code>Point3D</code> with <code>double</code> coordinates as input.
     *
     * @param x The x component of the data point
     * @param y The y component of the data point
     * @param z The z component of the data point
     * @param timestamp The time when this point was measured in milliseconds since 1.1.1970
     */
    @SuppressWarnings("unused") // Part of the API
    public Point3DImpl(final double x, final double y, final double z, final long timestamp) {
        this(Double.valueOf(x).floatValue(), Double.valueOf(y).floatValue(), Double.valueOf(z).floatValue(), timestamp);
    }

    /**
     * @return The x component of the data point
     */
    public float getX() {
        return x;
    }

    /**
     * @return The y component of the data point
     */
    public float getY() {
        return y;
    }

    /**
     * @return The z component of the data point
     */
    public float getZ() {
        return z;
    }

    /**
     * @param x The x component of the data point
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * @param y The y component of the data point
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * @param z The z component of the data point
     */
    public void setZ(float z) {
        this.z = z;
    }

    @Override
    public String toString() {
        return "Point X: " + x + "Y: " + y + "Z: " + z + " TS: " + getTimestamp();
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
        final var point3D = (Point3DImpl)o;
        return timestamp == point3D.timestamp && Float.compare(point3D.x, x) == 0 && Float.compare(point3D.y, y) == 0
                && Float.compare(point3D.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, x, y, z);
    }
}
