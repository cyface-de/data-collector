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

import de.cyface.model.Track;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;

/**
 * This implementation of the {@link MeasurementRetrievalStrategy} also loads the sensor data from the database when
 * retrieving measurements.
 * <p>
 * Attention: The sensor data is usually very large, so use the {@link MeasurementRetrievalWithoutSensorData} instead
 * when you only need the {@link Track} data.
 * <p>
 * TODO: To reduce the memory usage when loading a large measurement we could also not to load a full measurement
 * into memory but return a measurement "proxy" `MeasurementRetrievalWithSensorData`:
 * - this proxy has not yet loaded the measurement data but knows how to load the data on demand
 * - this proxy might have the ability to load itself on a WriteStream (piece by piece), depending on the output format
 * - i.e. this Measurement Interface MeasurementWriteStream
 */
public class MeasurementRetrievalWithSensorData extends MeasurementRetrievalImpl {

    @Override
    public FindOptions findOptions() {
        // Ensure the measurements are returned in order (or else we have flaky tests)
        final var sort = new JsonObject().put("metaData.deviceId", 1).put("metaData.measurementId", 1)
                .put("metaData.trackId", 1).put("timestamp", 1);
        return new FindOptions().setSort(sort);
    }
}
