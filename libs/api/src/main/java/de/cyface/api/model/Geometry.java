/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.api.model;

/**
 * This class represents a geometry similar to the GeoJson geometry field.
 *
 * @author Armin Schnabel
 * @since 1.1.0
 */
public class Geometry {

    /**
     * The GeoJson type of the geometry, e.g. "LineString".
     */
    private final String type;

    /**
     * The coordinates of this geometry.
     */
    private final Coordinate[] coordinates;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param type The GeoJson type of the geometry, e.g. "LineString".
     * @param coordinates The coordinates of this geometry.
     */
    public Geometry(String type, Coordinate[] coordinates) {
        this.type = type;
        this.coordinates = coordinates;
    }

    /**
     * @return The GeoJson type of the geometry, e.g. "LineString".
     */
    public String getType() {
        return type;
    }

    /**
     * @return The coordinates of this geometry.
     */
    public Coordinate[] getCoordinates() {
        return coordinates;
    }

    /**
     * A geo-coordinate.
     *
     * @author Armin Schnabel
     * @since 1.1.0
     */
    public static class Coordinate {
        /**
         * The latitude of this coordinate in degrees.
         */
        private final double latitude;
        /**
         * The longitude of this coordinate in degrees.
         */
        private final double longitude;

        /**
         * Constructs a fully initialized instance of this class.
         *
         * @param latitude The latitude of this coordinate in degrees.
         * @param longitude The longitude of this coordinate in degrees.
         */
        public Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /**
         * @return The latitude of this coordinate in degrees.
         */
        public double getLatitude() {
            return latitude;
        }

        /**
         * @return The longitude of this coordinate in degrees.
         */
        public double getLongitude() {
            return longitude;
        }

        @Override
        public String toString() {
            return "Coordinate{" +
                    "latitude=" + latitude +
                    ", longitude=" + longitude +
                    '}';
        }
    }
}
