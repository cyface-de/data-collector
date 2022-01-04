/*
 * Copyright 2018-2021 Cyface GmbH
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

import de.cyface.collector.handler.v2.FormAttributes;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * A POJO representing a single measurement, which has arrived at the API version 2 and needs to be stored to persistent storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 5.1.0
 * @since 2.0.0
 */
public final class Measurement implements Serializable {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = -1158500013112270899L;
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
    private static final int MAX_GENERIC_METADATA_FIELD_LENGTH = 30;
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
     * The number of files uploaded with a single request for API v2.
     */
    private static final int ACCEPTED_NUMBER_OF_FILES_V2 = 2;
    /**
     * The minimum valid amount of locations stored inside a measurement.
     */
    private static final long MINIMUM_LOCATION_COUNT = 0L;
    /**
     * The supported file extensions for the uploaded files. The file extensions have semantically meaning as they are
     * used to choose the deserialization method depending on the file type for transfer format 2.
     */
    @SuppressWarnings("SpellCheckingInspection")
    private static final String[] SUPPORTED_FILE_EXTENSIONS_V2 = {"ccyf", "ccyfe"};
    /**
     * The worldwide unique identifier of the device uploading the data.
     */
    private final String deviceIdentifier;
    /**
     * The device wide unique identifier of the uploaded measurement.
     */
    private final String measurementIdentifier;
    /**
     * The operating system version, such as Android 9.0.0 or iOS 11.2.
     */
    private final String operatingSystemVersion;
    /**
     * The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     */
    private final String deviceType;
    /**
     * The version of the app that transmitted the measurement.
     */
    private final String applicationVersion;
    /**
     * The length of the measurement in meters.
     */
    private final double length;
    /**
     * The count of geolocations in the transmitted measurement.
     */
    private final long locationCount;
    /**
     * The <code>GeoLocation</code> at the beginning of the track represented by the transmitted measurement.
     */
    private final GeoLocation startLocation;
    /**
     * The <code>GeoLocation</code> at the end of the track represented by the transmitted measurement.
     */
    private final GeoLocation endLocation;
    /**
     * The type of the vehicle that has captured the measurement.
     */
    private final String vehicle;
    /**
     * The name of the user uploading the measurement.
     */
    private final String username;
    /**
     * A list of files uploaded together with the measurement. These files contain the actual data.
     */
    private final Collection<FileUpload> fileUploads;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param deviceIdentifier       The worldwide unique identifier of the device uploading the data.
     * @param measurementIdentifier  The device wide unique identifier of the uploaded measurement.
     * @param operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
     * @param deviceType             The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     * @param applicationVersion     The version of the app that transmitted the measurement.
     * @param length                 The length of the measurement in meters.
     * @param locationCount          The count of geolocations in the transmitted measurement.
     * @param startLocation          The <code>GeoLocation</code> at the beginning of the track represented by the transmitted
     *                               measurement.
     * @param endLocation            The <code>GeoLocation</code> at the end of the track represented by the transmitted
     *                               measurement.
     * @param vehicle                The type of the vehicle that has captured the measurement.
     * @param username               The name of the user uploading the measurement.
     * @param fileUploads            A list of {@link FileUpload}s uploaded together with the measurement. These files contain the
     *                               actual data.
     */
    public Measurement(final String deviceIdentifier, final String measurementIdentifier,
                       final String operatingSystemVersion, final String deviceType, final String applicationVersion,
                       final double length, final long locationCount, final GeoLocation startLocation,
                       final GeoLocation endLocation, final String vehicle, final String username,
                       final Collection<FileUpload> fileUploads) {
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
        Validate.notNull(vehicle, "Field vehicleType was null!");
        Validate.isTrue(!vehicle.isEmpty() && vehicle.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field vehicleType had an invalid length of %d!", vehicle.length());
        Validate.notNull(username, "Field username was null!");
        Validate.isTrue(!username.isEmpty() && username.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field username had an invalid length of %d!", username.length());

        this.deviceIdentifier = deviceIdentifier;
        this.measurementIdentifier = measurementIdentifier;
        this.operatingSystemVersion = operatingSystemVersion;
        this.deviceType = deviceType;
        this.applicationVersion = applicationVersion;
        this.length = length;
        this.locationCount = locationCount;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.vehicle = vehicle;
        this.username = username;

        Validate.isTrue(fileUploads.size() == ACCEPTED_NUMBER_OF_FILES_V2,
                String.format("Measurement contained %d files but should contain exactly %d", fileUploads.size(),
                        ACCEPTED_NUMBER_OF_FILES_V2));

        for (final FileUpload fileUpload : fileUploads) {
            Validate.isTrue(Arrays.asList(SUPPORTED_FILE_EXTENSIONS_V2).contains(fileUpload.getFileType()),
                    "Measurement contained file with unsupported file type (file extension): %s",
                    fileUpload.getFileType());
        }

        this.fileUploads = fileUploads;
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
     * @return A list of files uploaded together with the measurement. These files contain the actual data.
     */
    public Collection<FileUpload> getFileUploads() {
        return fileUploads;
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
     * @return The <code>GeoLocation</code> at the beginning of the track represented by the transmitted measurement.
     */
    public GeoLocation getStartLocation() {
        return startLocation;
    }

    /**
     * @return The <code>GeoLocation</code> at the end of the track represented by the transmitted measurement.
     */
    public GeoLocation getEndLocation() {
        return endLocation;
    }

    /**
     * @return The type of the vehicle that has captured the measurement.
     */
    public String getVehicle() {
        return vehicle;
    }

    /**
     * @return The name of the user uploading the measurement.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return A JSON representation of this measurement.
     */
    public JsonObject toJson() {
        final JsonObject ret = new JsonObject();

        ret.put(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
        ret.put(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
        ret.put(FormAttributes.OS_VERSION.getValue(), operatingSystemVersion);
        ret.put(FormAttributes.DEVICE_TYPE.getValue(), deviceType);
        ret.put(FormAttributes.APPLICATION_VERSION.getValue(), applicationVersion);
        ret.put(FormAttributes.LENGTH.getValue(), length);
        ret.put(FormAttributes.LOCATION_COUNT.getValue(), locationCount);
        if (locationCount > 0) {
            ret.put(FormAttributes.START_LOCATION_LAT.getValue(), startLocation.getLat());
            ret.put(FormAttributes.START_LOCATION_LON.getValue(), startLocation.getLon());
            ret.put(FormAttributes.START_LOCATION_TS.getValue(), startLocation.getTimestamp());
            ret.put(FormAttributes.END_LOCATION_LAT.getValue(), endLocation.getLat());
            ret.put(FormAttributes.END_LOCATION_LON.getValue(), endLocation.getLon());
            ret.put(FormAttributes.END_LOCATION_TS.getValue(), endLocation.getTimestamp());
        }
        ret.put(FormAttributes.VEHICLE_TYPE.getValue(), vehicle);
        ret.put(FormAttributes.USERNAME.getValue(), username);

        return ret;
    }

    /**
     * @return A codec encoding and decoding this <code>Measurement</code> for usage on the event bus.
     */
    public static MessageCodec<Measurement, Measurement> getCodec() {
        return new EventBusCodec();

    }

    /**
     * A <code>MessageCodec</code> implementation to be used for transmitting a <code>Measurement</code> via a Vertx
     * event bus.
     *
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 1.0.0
     */
    static final class EventBusCodec implements MessageCodec<Measurement, Measurement> {
        @Override
        public void encodeToWire(final Buffer buffer, final Measurement serializable) {
            final var deviceIdentifier = serializable.getDeviceIdentifier();
            final var measurementIdentifier = serializable.getMeasurementIdentifier();
            final var deviceType = serializable.getDeviceType();
            final var operatingSystemVersion = serializable.getOperatingSystemVersion();
            final var applicationVersion = serializable.getApplicationVersion();
            final var length = serializable.getLength();
            final var locationCount = serializable.getLocationCount();
            final var vehicle = serializable.getVehicle();
            final var username = serializable.getUsername();

            buffer.appendInt(deviceIdentifier.length());
            buffer.appendInt(measurementIdentifier.length());
            buffer.appendInt(deviceType.length());
            buffer.appendInt(operatingSystemVersion.length());
            buffer.appendInt(applicationVersion.length());
            buffer.appendInt(vehicle.length());
            buffer.appendInt(username.length());

            buffer.appendInt(serializable.getFileUploads().size());

            buffer.appendString(deviceIdentifier);
            buffer.appendString(measurementIdentifier);
            buffer.appendString(deviceType);
            buffer.appendString(operatingSystemVersion);
            buffer.appendString(applicationVersion);
            buffer.appendDouble(length);
            buffer.appendLong(locationCount);
            buffer.appendString(vehicle);
            buffer.appendString(username);

            if (locationCount > 0) {
                final var startLocationLat = serializable.getStartLocation().getLat();
                final var startLocationLon = serializable.getStartLocation().getLon();
                final var startLocationTimestamp = serializable.getStartLocation().getTimestamp();
                final var endLocationLat = serializable.getEndLocation().getLat();
                final var endLocationLon = serializable.getEndLocation().getLon();
                final var endLocationTimestamp = serializable.getEndLocation().getTimestamp();
                buffer.appendDouble(startLocationLat);
                buffer.appendDouble(startLocationLon);
                buffer.appendLong(startLocationTimestamp);
                buffer.appendDouble(endLocationLat);
                buffer.appendDouble(endLocationLon);
                buffer.appendLong(endLocationTimestamp);
            }

            serializable.getFileUploads().forEach(fu -> {
                buffer.appendInt(fu.file.getAbsolutePath().length());
                buffer.appendString(fu.file.getAbsolutePath());
                buffer.appendInt(fu.fileType.length());
                buffer.appendString(fu.fileType);
            });
        }

        @Override
        public Measurement decodeFromWire(final int pos, final Buffer buffer) {
            Validate.isTrue(pos == 0, String.format("Pos %d not supported", pos));

            final var deviceIdentifierLength = buffer.getInt(0);
            final var measurementIdentifierLength = buffer.getInt(Integer.BYTES);
            final var deviceTypeLength = buffer.getInt(2 * Integer.BYTES);
            final var operatingSystemVersionLength = buffer.getInt(3 * Integer.BYTES);
            final var applicationVersionLength = buffer.getInt(4 * Integer.BYTES);
            final var vehicleTypeLength = buffer.getInt(5 * Integer.BYTES);
            final var usernameLength = buffer.getInt(6 * Integer.BYTES);
            final var numberOfFileUploads = buffer.getInt(7 * Integer.BYTES);

            final var deviceIdentifierEnd = 8 * Integer.BYTES + deviceIdentifierLength;
            final var deviceIdentifier = buffer.getString(8 * Integer.BYTES, deviceIdentifierEnd);
            final var measurementIdentifierEnd = deviceIdentifierEnd + measurementIdentifierLength;
            final var measurementIdentifier = buffer.getString(deviceIdentifierEnd, measurementIdentifierEnd);
            final var deviceTypeEnd = measurementIdentifierEnd + deviceTypeLength;
            final var deviceType = buffer.getString(measurementIdentifierEnd, deviceTypeEnd);
            final var operationSystemVersionEnd = deviceTypeEnd + operatingSystemVersionLength;
            final var operatingSystemVersion = buffer.getString(deviceTypeEnd, operationSystemVersionEnd);
            final var applicationVersionEnd = operationSystemVersionEnd + applicationVersionLength;
            final var applicationVersion = buffer.getString(operationSystemVersionEnd, applicationVersionEnd);

            final var lengthEnd = applicationVersionEnd + Double.BYTES;
            final var length = buffer.getDouble(applicationVersionEnd);
            final var locationCountEnd = lengthEnd + Long.BYTES;
            final var locationCount = buffer.getLong(lengthEnd);

            final var vehicleEnd = locationCountEnd + vehicleTypeLength;
            final var vehicle = buffer.getString(locationCountEnd, vehicleEnd);
            final var usernameEnd = vehicleEnd + usernameLength;
            final var username = buffer.getString(vehicleEnd, usernameEnd);

            GeoLocation startLocation = null;
            GeoLocation endLocation = null;
            var startOfFileUploads = usernameEnd;
            if (locationCount > 0) {
                final var startLocationLatEnd = usernameEnd + Double.BYTES;
                final var startLocationLonEnd = startLocationLatEnd + Double.BYTES;
                final var startLocationTimestampEnd = startLocationLonEnd + Long.BYTES;
                final var startLocationLat = buffer.getDouble(usernameEnd);
                final var startLocationLon = buffer.getDouble(startLocationLatEnd);
                final var startLocationTimestamp = buffer.getLong(startLocationLonEnd);

                final var endLocationLatEnd = startLocationTimestampEnd + Double.BYTES;
                final var endLocationLonEnd = endLocationLatEnd + Double.BYTES;
                final var endLocationTimestampEnd = endLocationLonEnd + Long.BYTES;
                final var endLocationLat = buffer.getDouble(startLocationTimestampEnd);
                final var endLocationLon = buffer.getDouble(endLocationLatEnd);
                final var endLocationTimestamp = buffer.getLong(endLocationLonEnd);
                startLocation = new GeoLocation(startLocationLat, startLocationLon,
                        startLocationTimestamp);
                endLocation = new GeoLocation(endLocationLat, endLocationLon, endLocationTimestamp);
                startOfFileUploads = endLocationTimestampEnd;
            }

            final var fileUploads = new HashSet<FileUpload>();
            var iterationStartByte = startOfFileUploads;
            for (int i = 0; i < numberOfFileUploads; i++) {
                final var entryLengthEnd = iterationStartByte + Integer.BYTES;
                final var entryLength = buffer.getInt(iterationStartByte);
                final var fileNameEnd = entryLengthEnd + entryLength;
                final var fileName = buffer.getString(entryLengthEnd, fileNameEnd);
                final var extensionLengthEnd = fileNameEnd + Integer.BYTES;
                final var extensionLength = buffer.getInt(fileNameEnd);
                final var fileTypeEnd = extensionLengthEnd + extensionLength;
                final var fileType = buffer.getString(extensionLengthEnd, fileTypeEnd);
                iterationStartByte = fileTypeEnd;

                final var uploadFile = new File(fileName);
                fileUploads.add(new FileUpload(uploadFile, fileType));
            }

            return new Measurement(deviceIdentifier, measurementIdentifier, operatingSystemVersion, deviceType,
                    applicationVersion, length, locationCount, startLocation, endLocation, vehicle, username,
                    fileUploads);
        }

        @Override
        public Measurement transform(final Measurement serializable) {
            return serializable;
        }

        @Override
        public String name() {
            return "v2.Measurement";
        }

        @Override
        public byte systemCodecID() {
            return -1;
        }
    }

    /**
     * A class which holds the uploaded file, and it's type, required to choose a deserialization strategy.
     *
     * @author Armin Schnabel
     * @version 1.0.0
     * @since 5.1.0
     */
    public static final class FileUpload {
        /**
         * A handle for the uploaded file on the local file system.
         */
        private final File file;
        /**
         * The type of the data stored in this file. This corresponds to the extension of the file. Compressed sensor
         * data for example is identified via the <tt>ccyf</tt> extension, while event files use <tt>ccyfe</tt>.
         */
        @SuppressWarnings("SpellCheckingInspection")
        private final String fileType;

        /**
         * @param file     A handle for the uploaded file on the local file system.
         * @param fileType The type of the data stored in this file. This corresponds to the extension of the file.
         *                 Compressed sensor data for example is identified via the <tt>ccyf</tt> extension, while event
         *                 files use <tt>ccyfe</tt>.
         */
        @SuppressWarnings("SpellCheckingInspection")
        public FileUpload(final File file, final String fileType) {
            this.file = file;
            this.fileType = fileType;
        }

        /**
         * @return A handle for the uploaded file on the local file system.
         */
        public File getFile() {
            return file;
        }

        /**
         * @return The type of the data stored in this file. This corresponds to the extension of the file. Compressed
         * sensor data for example is identified via the <tt>ccyf</tt> extension, while event files use
         * <tt>ccyfe</tt>.
         */
        @SuppressWarnings("SpellCheckingInspection")
        public String getFileType() {
            return fileType;
        }
    }
}
