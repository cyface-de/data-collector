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
package de.cyface.model;

import java.io.Serializable;
import java.nio.charset.Charset;

import org.apache.commons.lang3.Validate;

/**
 * The metadata as transmitted in the request header or pre-request body.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public class RequestMetaData implements Serializable {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = -1700430112854515404L;
    /**
     * The length of a universal unique identifier.
     */
    private static final int UUID_LENGTH = 36;
    /**
     * The default char set to use for encoding and decoding strings transmitted as metadata.
     */
    private static final String DEFAULT_CHARSET = "UTF-8";
    /**
     * Maximum size of a metadata field, with plenty space for future development. This prevents attackers from putting
     * arbitrary long data into these fields.
     */
    public static final int MAX_GENERIC_METADATA_FIELD_LENGTH = 30;
    /**
     * The maximum length of the measurement identifier in characters (this is the amount of characters of
     * {@value Long#MAX_VALUE}).
     */
    private static final int MAX_MEASUREMENT_ID_LENGTH = 20;
    /**
     * The minimum length of a track stored with a measurement.
     */
    private static final double MINIMUM_TRACK_LENGTH = 0.0;
    /**
     * The minimum valid amount of locations stored inside a measurement.
     */
    private static final long MINIMUM_LOCATION_COUNT = 0L;
    /**
     * The current version of the transferred file. This is always specified by the first two bytes of the file
     * transferred and helps compatible APIs to process data from different client versions.
     */
    public static final int CURRENT_TRANSFER_FILE_FORMAT_VERSION = 2;
    /**
     * The worldwide unique identifier of the device uploading the data.
     */
    final String deviceIdentifier;
    /**
     * The device wide unique identifier of the uploaded measurement.
     */
    final String measurementIdentifier;
    /**
     * The operating system version, such as Android 9.0.0 or iOS 11.2.
     */
    final String operatingSystemVersion;
    /**
     * The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     */
    final String deviceType;
    /**
     * The version of the app that transmitted the measurement.
     */
    final String applicationVersion;
    /**
     * The length of the measurement in meters.
     */
    final double length;
    /**
     * The count of geolocations in the transmitted measurement.
     */
    final long locationCount;
    /**
     * The {@code GeoLocation} at the beginning of the track represented by the transmitted measurement.
     */
    final GeoLocation startLocation;
    /**
     * The {@code GeoLocation} at the end of the track represented by the transmitted measurement.
     */
    final GeoLocation endLocation;
    /**
     * The modality type used to capture the measurement.
     */
    final String modality;
    /**
     * The format version of the upload file.
     */
    final int formatVersion;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param deviceIdentifier The worldwide unique identifier of the device uploading the data.
     * @param measurementIdentifier The device wide unique identifier of the uploaded measurement.
     * @param operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
     * @param deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     * @param applicationVersion The version of the app that transmitted the measurement.
     * @param length The length of the measurement in meters.
     * @param locationCount The count of geolocations in the transmitted measurement.
     * @param startLocation The {@code GeoLocation} at the beginning of the track represented by the transmitted
     *            measurement.
     * @param endLocation The {@code GeoLocation} at the end of the track represented by the transmitted measurement.
     * @param modality The modality type used to capture the measurement.
     * @param formatVersion The format version of the upload file.
     */
    public RequestMetaData(final String deviceIdentifier, final String measurementIdentifier,
            final String operatingSystemVersion, final String deviceType, final String applicationVersion,
            final double length, final long locationCount, final GeoLocation startLocation,
            final GeoLocation endLocation, final String modality, final int formatVersion) {

        Validate.notNull(deviceIdentifier, "Field deviceId was null!");
        Validate.isTrue(deviceIdentifier.getBytes(Charset.forName(DEFAULT_CHARSET)).length == UUID_LENGTH,
                "Field deviceId was not exactly 128 Bit, which is required for UUIDs!");
        Validate.notNull(deviceType, "Field deviceType was null!");
        Validate.isTrue(!deviceType.isEmpty() && deviceType.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field deviceType had an invalid length of %d!", deviceType.length());
        Validate.notNull(measurementIdentifier, "Field measurementId was null!");
        Validate.isTrue(
                !measurementIdentifier.isEmpty() && measurementIdentifier.length() <= MAX_MEASUREMENT_ID_LENGTH,
                "Field measurementId had an invalid length of %d!", measurementIdentifier.length());
        Validate.notNull(operatingSystemVersion, "Field osVersion was null!");
        Validate.isTrue(
                !operatingSystemVersion.isEmpty()
                        && operatingSystemVersion.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field osVersion had an invalid length of %d!", operatingSystemVersion.length());
        Validate.notNull(applicationVersion, "Field applicationVersion was null!");
        Validate.isTrue(
                !applicationVersion.isEmpty() && applicationVersion.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field applicationVersion had an invalid length of %d!", applicationVersion.length());
        Validate.isTrue(length >= MINIMUM_TRACK_LENGTH,
                "Field length had an invalid value %d which is smaller then 0.0!", length);
        Validate.isTrue(locationCount >= MINIMUM_LOCATION_COUNT,
                "Field locationCount had an invalid value %d which is smaller then 0!", locationCount);
        Validate.isTrue(locationCount == MINIMUM_LOCATION_COUNT || startLocation != null,
                "Start location should only be defined if there is at least one location in the uploaded track!");
        Validate.isTrue(locationCount == MINIMUM_LOCATION_COUNT || endLocation != null,
                "End location should only be defined if there is at least one location in the uploaded track!");
        Validate.notNull(modality, "Field modality was null!");
        Validate.isTrue(!modality.isEmpty() && modality.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field modality had an invalid length of %d!", modality.length());
        Validate.isTrue(formatVersion == CURRENT_TRANSFER_FILE_FORMAT_VERSION, "Unsupported formatVersion: %d",
                formatVersion);

        this.deviceIdentifier = deviceIdentifier;
        this.measurementIdentifier = measurementIdentifier;
        this.operatingSystemVersion = operatingSystemVersion;
        this.deviceType = deviceType;
        this.applicationVersion = applicationVersion;
        this.length = length;
        this.locationCount = locationCount;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.modality = modality;
        this.formatVersion = formatVersion;
    }

    /**
     * @return The worldwide unique identifier of the device uploading the data.
     */
    public String getDeviceIdentifier() {
        return deviceIdentifier;
    }

    /**
     * @return The device wide unique identifier of the uploaded measurement.
     */
    public String getMeasurementIdentifier() {
        return measurementIdentifier;
    }

    /**
     * @return The operating system version, such as Android 9.0.0 or iOS 11.2.
     */
    public String getOperatingSystemVersion() {
        return operatingSystemVersion;
    }

    /**
     * @return The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     */
    public String getDeviceType() {
        return deviceType;
    }

    /**
     * @return The version of the app that transmitted the measurement.
     */
    public String getApplicationVersion() {
        return applicationVersion;
    }

    /**
     * @return The length of the measurement in meters.
     */
    public double getLength() {
        return length;
    }

    /**
     * @return The count of geolocations in the transmitted measurement.
     */
    public long getLocationCount() {
        return locationCount;
    }

    /**
     * @return The {@code GeoLocation} at the beginning of the track represented by the transmitted measurement.
     */
    public GeoLocation getStartLocation() {
        return startLocation;
    }

    /**
     * @return The {@code GeoLocation} at the end of the track represented by the transmitted measurement.
     */
    public GeoLocation getEndLocation() {
        return endLocation;
    }

    /**
     * @return The modality type used to capture the measurement.
     */
    public String getModality() {
        return modality;
    }

    /**
     * @return The format version of the upload file.
     */
    public int getFormatVersion() {
        return formatVersion;
    }

    @Override
    public String toString() {
        return "RequestMetaData{" +
                "deviceIdentifier='" + deviceIdentifier + '\'' +
                ", measurementIdentifier='" + measurementIdentifier + '\'' +
                ", operatingSystemVersion='" + operatingSystemVersion + '\'' +
                ", deviceType='" + deviceType + '\'' +
                ", applicationVersion='" + applicationVersion + '\'' +
                ", length=" + length +
                ", locationCount=" + locationCount +
                ", startLocation=" + startLocation +
                ", endLocation=" + endLocation +
                ", modality='" + modality + '\'' +
                ", formatVersion=" + formatVersion +
                '}';
    }

    /**
     * This class represents a geolocation at the start or end of a track.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 6.0.0
     */
    public static class GeoLocation {
        /**
         * The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch).
         */
        private final long timestamp;
        /**
         * Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north).
         */
        private final double latitude;
        /**
         * Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180° (east).
         */
        private final double longitude;

        public GeoLocation(long timestamp, double latitude, double longitude) {
            this.timestamp = timestamp;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        @Override
        public String toString() {
            return "GeoLocation{" +
                    "timestamp=" + timestamp +
                    ", latitude=" + latitude +
                    ", longitude=" + longitude +
                    '}';
        }
    }
}