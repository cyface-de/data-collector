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
package de.cyface.collector.model;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

import de.cyface.collector.handler.FormAttributes;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

/**
 * A POJO representing a single measurement, which has arrived at the API and needs to be stored to persistent storage.
 * 
 * @author Klemens Muthmann
 * @version 4.0.0
 * @since 2.0.0
 */
public final class Measurement {
    /**
     * The world wide unique identifier of the device uploading the data.
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
     * The count of geo locations in the transmitted measurement.
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
    private final Collection<File> fileUploads;

    /**
     * Creates a new completely initialized object of this class.
     * 
     * @param deviceIdentifier The world wide unique identifier of the device uploading the data.
     * @param measurementIdentifier The device wide unique identifier of the uploaded measurement.
     * @param operatingSystemVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
     * @param deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     * @param applicationVersion The version of the app that transmitted the measurement.
     * @param length The length of the measurement in meters.
     * @param locationCount The count of geo locations in the transmitted measurement.
     * @param startLocation The <code>GeoLocation</code> at the beginning of the track represented by the transmitted
     *            measurement.
     * @param endLocation The <code>GeoLocation</code> at the end of the track represented by the transmitted
     *            measurement.
     * @param vehicle The type of the vehicle that has captured the measurement.
     * @param username The name of the user uploading the measurement.
     * @param fileUploads A list of files uploaded together with the measurement. These files contain the actual data.
     */
    public Measurement(final String deviceIdentifier, final String measurementIdentifier,
            final String operatingSystemVersion, final String deviceType, final String applicationVersion,
            final double length, final long locationCount, final GeoLocation startLocation,
            final GeoLocation endLocation, final String vehicle, final String username,
            final Collection<File> fileUploads) {
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
        this.fileUploads = fileUploads;
    }

    /**
     * @return The world wide unique identifier of the device uploading the data.
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
    public Collection<File> getFileUploads() {
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
     * @return The count of geo locations in the transmitted measurement.
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
        return new MessageCodec<Measurement, Measurement>() {

            @Override
            public void encodeToWire(final Buffer buffer, final Measurement s) {
                final String deviceIdentifier = s.getDeviceIdentifier();
                final String measurementIdentifier = s.getMeasurementIdentifier();
                final String deviceType = s.getDeviceType();
                final String operatingSystemVersion = s.getOperatingSystemVersion();
                final String applicationVersion = s.getApplicationVersion();
                final double length = s.getLength();
                final long locationCount = s.getLocationCount();
                final String vehicle = s.getVehicle();
                final String username = s.getUsername();

                buffer.appendInt(deviceIdentifier.length());
                buffer.appendInt(measurementIdentifier.length());
                buffer.appendInt(deviceType.length());
                buffer.appendInt(operatingSystemVersion.length());
                buffer.appendInt(applicationVersion.length());
                buffer.appendInt(vehicle.length());
                buffer.appendInt(username.length());
                buffer.appendInt(s.getFileUploads().size());
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
                    final double startLocationLat = s.getStartLocation().getLat();
                    final double startLocationLon = s.getStartLocation().getLon();
                    final long startLocationTimestamp = s.getStartLocation().getTimestamp();
                    final double endLocationLat = s.getEndLocation().getLat();
                    final double endLocationLon = s.getEndLocation().getLon();
                    final long endLocationTimestamp = s.getEndLocation().getTimestamp();
                    buffer.appendDouble(startLocationLat);
                    buffer.appendDouble(startLocationLon);
                    buffer.appendLong(startLocationTimestamp);
                    buffer.appendDouble(endLocationLat);
                    buffer.appendDouble(endLocationLon);
                    buffer.appendLong(endLocationTimestamp);
                }
                s.getFileUploads().forEach(fu -> {
                    buffer.appendInt(fu.getAbsolutePath().length());
                    buffer.appendString(fu.getAbsolutePath());
                });
            }

            @Override
            public Measurement decodeFromWire(final int pos, final Buffer buffer) {
                final int deviceIdentifierLength = buffer.getInt(0 * Integer.BYTES);
                final int measurementIdentifierLength = buffer.getInt(1 * Integer.BYTES);
                final int deviceTypeLength = buffer.getInt(2 * Integer.BYTES);
                final int operatingSystemVersionLength = buffer.getInt(3 * Integer.BYTES);
                final int applicationVersionLength = buffer.getInt(4 * Integer.BYTES);
                final int vehicleTypeLength = buffer.getInt(5 * Integer.BYTES);
                final int usernameLength = buffer.getInt(6 * Integer.BYTES);
                final int numberOfFileUploads = buffer.getInt(7 * Integer.BYTES);

                final int deviceIdentifierEnd = 8 * Integer.BYTES + deviceIdentifierLength;
                final String deviceIdentifier = buffer.getString(8 * Integer.BYTES, deviceIdentifierEnd);
                final int measurementIdentifierEnd = deviceIdentifierEnd + measurementIdentifierLength;
                final String measurementIdentifier = buffer.getString(deviceIdentifierEnd, measurementIdentifierEnd);
                final int deviceTypeEnd = measurementIdentifierEnd + deviceTypeLength;
                final String deviceType = buffer.getString(measurementIdentifierEnd, deviceTypeEnd);
                final int operationSystemVersionEnd = deviceTypeEnd + operatingSystemVersionLength;
                final String operatingSystemVersion = buffer.getString(deviceTypeEnd, operationSystemVersionEnd);
                final int applicationVersionEnd = operationSystemVersionEnd + applicationVersionLength;
                final String applicationVersion = buffer.getString(operationSystemVersionEnd, applicationVersionEnd);
                final int lengthEnd = applicationVersionEnd + Double.BYTES;
                final double length = buffer.getDouble(applicationVersionEnd);
                final int locationCountEnd = lengthEnd + Long.BYTES;
                final long locationCount = buffer.getLong(lengthEnd);
                final int vehicleEnd = locationCountEnd + vehicleTypeLength;
                final String vehicle = buffer.getString(locationCountEnd, vehicleEnd);
                final int usernameEnd = vehicleEnd + usernameLength;
                final String username = buffer.getString(vehicleEnd, usernameEnd);

                GeoLocation startLocation = null;
                GeoLocation endLocation = null;
                int startOfFileUploads = usernameEnd;
                if (locationCount > 0) {
                    final int startLocationLatEnd = usernameEnd + Double.BYTES;
                    final int startLocationLonEnd = startLocationLatEnd + Double.BYTES;
                    final int startLocationTimestampEnd = startLocationLonEnd + Double.BYTES;
                    final double startLocationLat = buffer.getDouble(usernameEnd);
                    final double startLocationLon = buffer.getDouble(startLocationLatEnd);
                    final long startLocationTimestamp = buffer.getLong(startLocationLonEnd);

                    final int endLocationLatEnd = startLocationTimestampEnd + Double.BYTES;
                    final int endLocationLonEnd = endLocationLatEnd + Double.BYTES;
                    final int endLocationTimestampEnd = endLocationLonEnd + Long.BYTES;
                    final double endLocationLat = buffer.getDouble(startLocationTimestampEnd);
                    final double endLocationLon = buffer.getDouble(endLocationLatEnd);
                    final long endLocationTimestamp = buffer.getLong(endLocationLonEnd);
                    startLocation = new GeoLocation(startLocationLat, startLocationLon,
                            startLocationTimestamp);
                    endLocation = new GeoLocation(endLocationLat, endLocationLon, endLocationTimestamp);
                    startOfFileUploads = endLocationTimestampEnd;
                }

                Collection<File> fileUploads = new HashSet<>();
                int iterationStartByte = startOfFileUploads;
                for (int i = 0; i < numberOfFileUploads; i++) {
                    int entryLength = buffer.getInt(iterationStartByte);
                    String fileName = buffer.getString(4 + iterationStartByte, 4 + iterationStartByte + entryLength);
                    iterationStartByte += 4 + iterationStartByte + entryLength;

                    File uploadFile = new File(fileName);
                    fileUploads.add(uploadFile);
                }

                return new Measurement(deviceIdentifier, measurementIdentifier, operatingSystemVersion, deviceType,
                        applicationVersion, length, locationCount, startLocation, endLocation, vehicle, username,
                        fileUploads);
            }

            @Override
            public Measurement transform(final Measurement s) {
                return s;
            }

            @Override
            public String name() {
                return "Measurement";
            }

            @Override
            public byte systemCodecID() {
                return -1;
            }
        };

    }

}
