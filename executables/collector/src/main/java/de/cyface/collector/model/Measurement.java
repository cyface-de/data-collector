/*
 * Copyright 2018-2022 Cyface GmbH
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
import java.io.Serializable;

import org.apache.commons.lang3.Validate;

import de.cyface.collector.handler.FormAttributes;
import de.cyface.model.RequestMetaData;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.bson.types.ObjectId;

/**
 * A POJO representing a single measurement, which has arrived at the API version 3 and needs to be stored to persistent
 * storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 7.0.0
 * @since 2.0.0
 */
public final class Measurement implements Serializable {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = -8304842300727933736L;
    /**
     * The database field name which contains the user id of the measurement owner.
     */
    public static String USER_ID_FIELD = "userId";
    /**
     * the metadata from the request header.
     */
    private final RequestMetaData metaData;
    /**
     * The id of the user uploading the measurement.
     */
    private final String userId;
    /**
     * The binary uploaded with the measurement. This contains the actual data.
     */
    private final File binary;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param metaData the metadata from the request header.
     * @param userId The id of the user uploading the measurement.
     * @param binary The binary uploaded together with the measurement. This contains the actual data.
     */
    public Measurement(final RequestMetaData metaData, final String userId, final File binary) {

        Validate.notNull(userId, "Field userId was null!");

        this.metaData = metaData;
        this.userId = userId;
        this.binary = binary;
    }

    /**
     * @return the metadata from the request header.
     */
    public RequestMetaData getMetaData() {
        return metaData;
    }

    /**
     * @return The id of the user uploading the measurement.
     */
    public String getUserId() {
        return userId;
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
        final var ret = new JsonObject();

        ret.put(FormAttributes.DEVICE_ID.getValue(), metaData.getDeviceIdentifier());
        ret.put(FormAttributes.MEASUREMENT_ID.getValue(), metaData.getMeasurementIdentifier());
        ret.put(FormAttributes.OS_VERSION.getValue(), metaData.getOperatingSystemVersion());
        ret.put(FormAttributes.DEVICE_TYPE.getValue(), metaData.getDeviceType());
        ret.put(FormAttributes.APPLICATION_VERSION.getValue(), metaData.getApplicationVersion());
        ret.put(FormAttributes.LENGTH.getValue(), metaData.getLength());
        ret.put(FormAttributes.LOCATION_COUNT.getValue(), metaData.getLocationCount());
        if (metaData.getLocationCount() > 0) {
            ret.put("start", geoJson(metaData.getStartLocation()));
            ret.put("end", geoJson(metaData.getEndLocation()));
        }
        ret.put(FormAttributes.MODALITY.getValue(), metaData.getModality());
        ret.put(USER_ID_FIELD, new ObjectId(userId));
        ret.put(FormAttributes.FORMAT_VERSION.getValue(), metaData.getFormatVersion());

        return ret;
    }

    /**
     * Converts a location record into {@code JSON} which supports the mongoDB {@code GeoJSON} format:
     * https://docs.mongodb.com/manual/geospatial-queries/
     *
     * @param record the location record to be converted
     * @return the converted location record as JSON
     */
    private JsonObject geoJson(final RequestMetaData.GeoLocation record) {
        final var lat = record.getLatitude();
        final var lon = record.getLongitude();
        final var ts = record.getTimestamp();
        final var geometry = new JsonObject()
                .put("type", "Point")
                .put("coordinates", new JsonArray().add(lon).add(lat));
        return new JsonObject()
                .put("location", geometry)
                .put("timestamp", ts);
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
            final var modality = metaData.getModality();
            final String userId = serializable.getUserId();

            buffer.appendInt(deviceIdentifier.length());
            buffer.appendInt(measurementIdentifier.length());
            buffer.appendInt(deviceType.length());
            buffer.appendInt(operatingSystemVersion.length());
            buffer.appendInt(applicationVersion.length());
            buffer.appendInt(modality.length());
            buffer.appendInt(userId.length());

            buffer.appendInt(metaData.getFormatVersion());
            buffer.appendString(deviceIdentifier);
            buffer.appendString(measurementIdentifier);
            buffer.appendString(deviceType);
            buffer.appendString(operatingSystemVersion);
            buffer.appendString(applicationVersion);
            buffer.appendDouble(length);
            buffer.appendLong(locationCount);
            buffer.appendString(modality);
            buffer.appendString(userId);

            if (locationCount > 0) {
                final var startLocationLat = metaData.getStartLocation().getLatitude();
                final var startLocationLon = metaData.getStartLocation().getLongitude();
                final var startLocationTimestamp = metaData.getStartLocation().getTimestamp();
                final var endLocationLat = metaData.getEndLocation().getLatitude();
                final var endLocationLon = metaData.getEndLocation().getLongitude();
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
            final var modalityLength = buffer.getInt(5 * Integer.BYTES);
            final var usernameLength = buffer.getInt(6 * Integer.BYTES);
            final var formatVersion = buffer.getInt(7 * Integer.BYTES);
            Validate.isTrue(formatVersion == 3);

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

            final var modalityEnd = locationCountEnd + modalityLength;
            final var modality = buffer.getString(locationCountEnd, modalityEnd);
            final var usernameEnd = modalityEnd + usernameLength;
            final var userId = buffer.getString(modalityEnd, usernameEnd);

            RequestMetaData.GeoLocation startLocation = null;
            RequestMetaData.GeoLocation endLocation = null;
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
                startLocation = new RequestMetaData.GeoLocation(startLocationTimestamp, startLocationLat,
                        startLocationLon);
                endLocation = new RequestMetaData.GeoLocation(endLocationTimestamp, endLocationLat, endLocationLon);
                startOfFileUploads = endLocationTimestampEnd;
            }

            final var entryLengthEnd = startOfFileUploads + Integer.BYTES;
            final var entryLength = buffer.getInt(startOfFileUploads);
            final var fileNameEnd = entryLengthEnd + entryLength;
            final var fileName = buffer.getString(entryLengthEnd, fileNameEnd);
            final var uploadFile = new File(fileName);
            final var metaData = new RequestMetaData(deviceIdentifier, measurementIdentifier, operatingSystemVersion,
                    deviceType, applicationVersion, length, locationCount, startLocation, endLocation, modality,
                    formatVersion);

            return new Measurement(metaData, userId, uploadFile);
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
