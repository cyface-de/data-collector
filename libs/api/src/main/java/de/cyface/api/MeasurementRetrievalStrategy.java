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

import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.MetaData;
import de.cyface.model.Point3DImpl;
import de.cyface.model.RawRecord;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;

/**
 * This class defines the interface to allow multiple {@link MeasurementRetrievalStrategy}s such as
 * {@link MeasurementRetrievalWithSensorData} or {@link MeasurementRetrievalWithoutSensorData}.
 *
 * @author Armin Schnabel
 * @since 6.0.0
 */
public interface MeasurementRetrievalStrategy {

    /**
     * Returns the {@code FindOptions} which define which data to load from the database, e.g. all but sensor data.
     *
     * @return the {@code FindOptions} to be used for database access
     */
    FindOptions findOptions();

    /**
     * Returns the {@code trackId}.
     *
     * @param document the {@code Document} to extract the trackId from.
     * @return the trackId
     */
    int trackId(final JsonObject document);

    /**
     * Returns the {@code MetaData} as POJO.
     *
     * @param document the {@code Document} containing the measurement, {@link MetaData#getVersion()} 1.0.0.
     * @return the metadata POJO
     */
    MetaData metaData(final JsonObject document);

    /**
     * Returns the {@code GeoLocationRecord} as POJO.
     *
     * @param doc the {@code Document} containing the {@code GeoLocation} in {@link MetaData#getVersion()} 1.0.0.
     * @param identifier the identifier of the measurement of this location
     * @return the record POJO
     */
    RawRecord geoLocation(final JsonObject doc, final MeasurementIdentifier identifier);

    /**
     * Returns the {@code Point3D} as POJO.
     *
     * @param doc the {@code Document} containing the {@code Point3D} in {@link MetaData#getVersion()} 1.0.0.
     * @return the point POJO
     */
    Point3DImpl point3D(final JsonObject doc);
}
