/*
 * Copyright 2018 Cyface GmbH
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
 * @version 1.0.0
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
     * @param fileUploads A list of files uploaded together with the measurement. These files contain the actual data.
     */
    public Measurement(final String deviceIdentifier, final String measurementIdentifier,
            final String operatingSystemVersion, final String deviceType, final Collection<File> fileUploads) {
        this.deviceIdentifier = deviceIdentifier;
        this.measurementIdentifier = measurementIdentifier;
        this.operatingSystemVersion = operatingSystemVersion;
        this.deviceType = deviceType;
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
     * @return A JSON representation of this measurement.
     */
    public JsonObject toJson() {
        JsonObject ret = new JsonObject();

        ret.put(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
        ret.put(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
        ret.put(FormAttributes.OS_VERSION.getValue(), operatingSystemVersion);
        ret.put(FormAttributes.DEVICE_TYPE.getValue(), deviceType);

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

                buffer.appendInt(deviceIdentifier.length());
                buffer.appendInt(measurementIdentifier.length());
                buffer.appendInt(deviceType.length());
                buffer.appendInt(operatingSystemVersion.length());
                buffer.appendInt(s.getFileUploads().size());
                buffer.appendString(deviceIdentifier);
                buffer.appendString(measurementIdentifier);
                buffer.appendString(deviceType);
                buffer.appendString(operatingSystemVersion);
                s.getFileUploads().forEach(fu -> {
                    buffer.appendInt(fu.getAbsolutePath().length());
                    buffer.appendString(fu.getAbsolutePath());
                });
            }

            @Override
            public Measurement decodeFromWire(int pos, final Buffer buffer) {
                int deviceIdentifierLength = buffer.getInt(0);
                int measurementIdentifierLength = buffer.getInt(4);
                int deviceTypeLength = buffer.getInt(8);
                int operatingSystemVersionLength = buffer.getInt(12);
                int numberOfFileUploads = buffer.getInt(16);

                int deviceIdentifierEnd = 16 + deviceIdentifierLength;
                final String deviceIdentifier = buffer.getString(20, deviceIdentifierEnd);
                int measurementIdentifierEnd = deviceIdentifierEnd + measurementIdentifierLength;
                final String measurementIdentifier = buffer.getString(deviceIdentifierEnd, measurementIdentifierEnd);
                int deviceTypeEnd = measurementIdentifierEnd + deviceTypeLength;
                final String deviceType = buffer.getString(measurementIdentifierEnd, deviceTypeEnd);
                int operationSystemVersionEnd = deviceTypeEnd + operatingSystemVersionLength;
                final String operatingSystemVersion = buffer.getString(deviceTypeEnd, operationSystemVersionEnd);

                Collection<File> fileUploads = new HashSet<>();
                int iterationStartByte = operationSystemVersionEnd;
                for (int i = 0; i < numberOfFileUploads; i++) {
                    int entryLength = buffer.getInt(operationSystemVersionEnd);
                    String fileName = buffer.getString(4 + iterationStartByte, 4 + iterationStartByte + entryLength);
                    iterationStartByte += 4 + iterationStartByte + entryLength;

                    File uploadFile = new File(fileName);
                    fileUploads.add(uploadFile);
                }

                return new Measurement(deviceIdentifier, measurementIdentifier, operatingSystemVersion, deviceType,
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
