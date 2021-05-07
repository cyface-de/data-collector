/*
 * Copyright 2018, 2019 Cyface GmbH
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
package de.cyface.collector.handler;

/**
 * Attributes supported by the APIs multipart form upload POST endpoint.
 * 
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.1
 * @since 2.0.0
 */
public enum FormAttributes {
    /**
     * The world wide unique identifier of the device uploading the data.
     */
    DEVICE_ID("deviceId"),
    /**
     * The device wide unique identifier of the uploaded measurement.
     */
    MEASUREMENT_ID("measurementId"),
    /**
     * The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     */
    DEVICE_TYPE("deviceType"),
    /**
     * The operating system version, such as Android 9.0.0 or iOS 11.2.
     */
    OS_VERSION("osVersion"),
    /**
     * The version of the app that transmitted the measurement.
     */
    APPLICATION_VERSION("appVersion"),
    /**
     * The length of the measurement in meters.
     */
    LENGTH("length"),
    /**
     * The count of geo locations in the transmitted measurement.
     */
    LOCATION_COUNT("locationCount"),
    /**
     * The latitude of the geo location at the beginning of the track represented by the transmitted measurement. This
     * value is optional and may not be available for measurements without locations. For measurements with one location
     * this equals the {@link #END_LOCATION_LAT}.
     */
    START_LOCATION_LAT("startLocLat"),
    /**
     * The longitude of the geo location at the beginning of the track represented by the transmitted measurement. This
     * value is optional and may not be available for measurements without locations. For measurements with one location
     * this equals the {@link #END_LOCATION_LON}.
     */
    START_LOCATION_LON("startLocLon"),
    /**
     * The timestamp in milliseconds of the geo location at the beginning of the track represented by the transmitted
     * measurement. This value is optional and may not be available for measurements without locations. For measurements
     * with one location this equals the {@link #END_LOCATION_TS}.
     */
    START_LOCATION_TS("startLocTS"),
    /**
     * The latitude of the geo location at the end of the track represented by the transmitted measurement. This value
     * is optional and may not be available for measurements without locations. For measurements
     * with one location this equals the {@link #START_LOCATION_LAT}.
     */
    END_LOCATION_LAT("endLocLat"),
    /**
     * The longitude of the geo location at the end of the track represented by the transmitted measurement. This value
     * is optional and may not be available for measurements without locations. For measurements
     * with one location this equals the {@link #START_LOCATION_LON}.
     */
    END_LOCATION_LON("endLocLon"),
    /**
     * The timestamp in milliseconds of the geo location at the end of the track represented by the transmitted
     * measurement. This value is optional and may not be available for measurements without locations. For measurements
     * with one location this equals the {@link #START_LOCATION_TS}.
     */
    END_LOCATION_TS("endLocTS"),
    /**
     * The type of the vehicle that has captured the measurement.
     */
    VEHICLE_TYPE("vehicle"),
    /**
     * The name of the user uploading the measurement.
     */
    USERNAME("username");

    /**
     * The value identifying the attribute in the multipart form request.
     */
    private final String value;

    /**
     * Creates a new completely initialized multipart form attribute.
     * 
     * @param value The value identifying the attribute in the multipart form request.
     */
    FormAttributes(final String value) {
        this.value = value;
    }

    /**
     * @return The value identifying the attribute in the multipart form request.
     */
    public String getValue() {
        return value;
    }
}
