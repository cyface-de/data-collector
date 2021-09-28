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
package de.cyface.collector.model;

import static de.cyface.collector.model.RequestMetaData.MAX_GENERIC_METADATA_FIELD_LENGTH;

import java.io.File;

import org.apache.commons.lang3.Validate;

import de.cyface.collector.handler.FormAttributes;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

/**
 * A POJO representing a single measurement, which has arrived at the API version 3 and needs to be stored to persistent
 * storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 6.0.0
 * @since 2.0.0
 */
public final class Measurement {
    /**
     * the metadata from the request header.
     */
    private final RequestMetaData metaData;
    /**
     * The name of the user uploading the measurement.
     */
    private final String username;
    /**
     * The binary uploaded with the measurement. This contains the actual data.
     */
    private final File binary;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param metaData the metadata from the request header.
     * @param username The name of the user uploading the measurement.
     * @param binary The binary uploaded together with the measurement. This contains the actual data.
     */
    public Measurement(final RequestMetaData metaData, final String username, final File binary) {

        Validate.notNull(username, "Field username was null!");
        Validate.isTrue(!username.isEmpty() && username.length() <= MAX_GENERIC_METADATA_FIELD_LENGTH,
                "Field username had an invalid length of %d!", username.length());

        this.metaData = metaData;
        this.username = username;
        this.binary = binary;
    }

    /**
     * @return the metadata from the request header.
     */
    public RequestMetaData getMetaData() {
        return metaData;
    }

    /**
     * @return The name of the user uploading the measurement.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return The binary uploaded with the measurement. This contains the actual data.
     */
    public File getBinary() {
        return binary;
    }

    /**
     * @return A JSON representation of this measurement.
     */
    public JsonObject toJson() {
        final JsonObject ret = new JsonObject();

        ret.put(FormAttributes.DEVICE_ID.getValue(), metaData.deviceIdentifier);
        ret.put(FormAttributes.MEASUREMENT_ID.getValue(), metaData.measurementIdentifier);
        ret.put(FormAttributes.OS_VERSION.getValue(), metaData.operatingSystemVersion);
        ret.put(FormAttributes.DEVICE_TYPE.getValue(), metaData.deviceType);
        ret.put(FormAttributes.APPLICATION_VERSION.getValue(), metaData.applicationVersion);
        ret.put(FormAttributes.LENGTH.getValue(), metaData.length);
        ret.put(FormAttributes.LOCATION_COUNT.getValue(), metaData.locationCount);
        if (metaData.locationCount > 0) {
            ret.put(FormAttributes.START_LOCATION_LAT.getValue(), metaData.startLocation.getLat());
            ret.put(FormAttributes.START_LOCATION_LON.getValue(), metaData.startLocation.getLon());
            ret.put(FormAttributes.START_LOCATION_TS.getValue(), metaData.startLocation.getTimestamp());
            ret.put(FormAttributes.END_LOCATION_LAT.getValue(), metaData.endLocation.getLat());
            ret.put(FormAttributes.END_LOCATION_LON.getValue(), metaData.endLocation.getLon());
            ret.put(FormAttributes.END_LOCATION_TS.getValue(), metaData.endLocation.getTimestamp());
        }
        ret.put(FormAttributes.VEHICLE_TYPE.getValue(), metaData.vehicle);
        ret.put(FormAttributes.USERNAME.getValue(), username);
        ret.put(FormAttributes.FORMAT_VERSION.getValue(), metaData.formatVersion);

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
     * @author Armin Schnabel
     * @version 2.0.0
     * @since 1.0.0
     */
    static final class EventBusCodec implements MessageCodec<Measurement, Measurement> {

        @Override
        public void encodeToWire(final Buffer buffer, final Measurement serializable) {
            final var metaData = serializable.metaData;
            final var deviceIdentifier = metaData.getDeviceIdentifier();
            final var measurementIdentifier = metaData.getMeasurementIdentifier();
            final var deviceType = metaData.getDeviceType();
            final var operatingSystemVersion = metaData.getOperatingSystemVersion();
            final var applicationVersion = metaData.getApplicationVersion();
            final var length = metaData.getLength();
            final var locationCount = metaData.getLocationCount();
            final var vehicle = metaData.getVehicle();
            final var username = serializable.getUsername();

            buffer.appendInt(deviceIdentifier.length());
            buffer.appendInt(measurementIdentifier.length());
            buffer.appendInt(deviceType.length());
            buffer.appendInt(operatingSystemVersion.length());
            buffer.appendInt(applicationVersion.length());
            buffer.appendInt(vehicle.length());
            buffer.appendInt(username.length());

            buffer.appendInt(metaData.getFormatVersion());
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
                final var startLocationLat = metaData.getStartLocation().getLat();
                final var startLocationLon = metaData.getStartLocation().getLon();
                final var startLocationTimestamp = metaData.getStartLocation().getTimestamp();
                final var endLocationLat = metaData.getEndLocation().getLat();
                final var endLocationLon = metaData.getEndLocation().getLon();
                final var endLocationTimestamp = metaData.getEndLocation().getTimestamp();
                buffer.appendDouble(startLocationLat);
                buffer.appendDouble(startLocationLon);
                buffer.appendLong(startLocationTimestamp);
                buffer.appendDouble(endLocationLat);
                buffer.appendDouble(endLocationLon);
                buffer.appendLong(endLocationTimestamp);
            }

            // file upload (always one binary in version 3)
            final var fu = serializable.binary;
            buffer.appendInt(fu.getAbsolutePath().length());
            buffer.appendString(fu.getAbsolutePath());
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
            final var formatVersion = buffer.getInt(7 * Integer.BYTES);
            Validate.isTrue(formatVersion == 2);

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

            final var entryLengthEnd = startOfFileUploads + Integer.BYTES;
            final var entryLength = buffer.getInt(startOfFileUploads);
            final var fileNameEnd = entryLengthEnd + entryLength;
            final var fileName = buffer.getString(entryLengthEnd, fileNameEnd);
            final var uploadFile = new File(fileName);
            final var metaData = new RequestMetaData(deviceIdentifier, measurementIdentifier, operatingSystemVersion,
                    deviceType, applicationVersion, length, locationCount, startLocation, endLocation, vehicle,
                    formatVersion);

            return new Measurement(metaData, username, uploadFile);
        }

        @Override
        public Measurement transform(final Measurement serializable) {
            return serializable;
        }

        @Override
        public String name() {
            return "v3.Measurement";
        }

        @Override
        public byte systemCodecID() {
            return -1;
        }
    }
}
