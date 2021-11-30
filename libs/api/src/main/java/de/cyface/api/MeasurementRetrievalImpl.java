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
package de.cyface.api;

import org.apache.commons.lang3.Validate;

import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.MetaData;
import de.cyface.model.Modality;
import de.cyface.model.Point3DImpl;
import de.cyface.model.RawRecord;
import io.vertx.core.json.JsonObject;

/**
 * This class defines methods required by all {@link MeasurementRetrievalStrategy} implementations.
 *
 * @author Armin Schnabel
 * @since 6.0.0
 */
public abstract class MeasurementRetrievalImpl implements MeasurementRetrievalStrategy {

    public int trackId(final JsonObject document) {
        return document.getJsonObject("metaData").getInteger("trackId");
    }

    public MetaData metaData(final JsonObject document) {
        final var metaData = document.getJsonObject("metaData");
        final var version = metaData.getString("version");
        Validate.isTrue(version.equals(MetaData.CURRENT_VERSION),
                "Encountered data in invalid format. Only Cyface Format Version 1.0.0 is supported!");

        final var identifier = new MeasurementIdentifier(metaData.getString("deviceId"),
                metaData.getLong("measurementId"));
        final var deviceType = metaData.getString("deviceType");
        final var osVersion = metaData.getString("osVersion");
        final var appVersion = metaData.getString("appVersion");
        final var length = metaData.getDouble("length");
        final var username = metaData.getString("username");
        return new MetaData(identifier, deviceType, osVersion, appVersion, length, username, version);
    }

    /**
     * Returns the {@code GeoLocationRecord} as POJO.
     *
     * @param doc the {@code Document} containing the {@code GeoLocation} in {@link MetaData#getVersion()} 1.0.0.
     * @param identifier the identifier of the measurement of this location
     * @return the record POJO
     */
    public RawRecord geoLocation(final JsonObject doc, final MeasurementIdentifier identifier) {
        final var instant = doc.getJsonObject("timestamp").getInstant("$date");
        final long timestamp = instant.toEpochMilli();
        final var value = doc.getJsonObject("value");
        final var geometry = value.getJsonObject("geometry");
        Validate.isTrue(geometry.getString("type").equals("Point"));
        final var coordinates = geometry.getJsonArray("coordinates");
        final var latitude = coordinates.getDouble(1);
        final var longitude = coordinates.getDouble(0);
        final var elevation = value.getDouble("elevation");
        final var speed = value.getDouble("speed");
        final var accuracy = value.getDouble("accuracy");
        final var modality = Modality.valueOf(value.getString("modality"));
        Validate.notNull(modality, "Unable to identify modality type: " + value.getString("modality"));
        return new RawRecord(identifier, timestamp, latitude, longitude, elevation, accuracy, speed, modality);
    }

    public Point3DImpl point3D(final JsonObject doc) {
        final var timestamp = doc.getLong("timestamp");
        // MongoDB stores all numbers in the same data type
        final var value = doc.getJsonObject("value");
        final var x = value.getDouble("x").floatValue();
        final var y = value.getDouble("y").floatValue();
        final var z = value.getDouble("z").floatValue();
        return new Point3DImpl(x, y, z, timestamp);
    }
}
