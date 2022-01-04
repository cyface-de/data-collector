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
package de.cyface.collector.model.v2;

import java.io.Serializable;

/**
 * A model of a geo location with latitude and longitude captured at a certain time.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.0.0
 */
public final class GeoLocation implements Serializable {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = -8668827679439438824L;
    /**
     * The geographical latitude of this location.
     */
    private final double lat;
    /**
     * The geographical longitude of this location.
     */
    private final double lon;
    /**
     * The time this location was captured at in milliseconds since the first of january 1970.
     */
    private final long timestamp;

    /**
     * Creates a new completely initialized immutable instance of this class.
     * 
     * @param lat The geographical latitude of this location.
     * @param lon The geographical longitude of this location.
     * @param timestamp The time this location was captured at in milliseconds since the first of january 1970.
     */
    public GeoLocation(final double lat, final double lon, final long timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }

    /**
     * @return The geographical latitude of this location.
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The geographical longitude of this location.
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The time this location was captured at in milliseconds since the first of january 1970.
     */
    public long getTimestamp() {
        return timestamp;
    }
}
