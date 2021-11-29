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

import java.text.ParseException;
import java.util.ArrayList;

import de.cyface.api.model.TrackBucket;
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
        return new FindOptions();
    }

    @Override
    public TrackBucket trackBucket(final JsonObject document) throws ParseException {

        final var metaData = metaData(document);
        // Avoiding having a Track constructor from Document to avoid mongodb dependency in model library
        final var trackDocument = document.getJsonObject("track");
        final var trackId = trackDocument.getInteger("trackId");
        final var bucket = trackDocument.getJsonObject("bucket");
        final var geoLocationsDocuments = trackDocument.getJsonArray("geoLocations");
        final var locationRecords = geoLocations(geoLocationsDocuments, metaData.getIdentifier());

        final var accelerationsDocuments = trackDocument.getJsonArray("accelerations");
        final var rotationsDocuments = trackDocument.getJsonArray("rotations");
        final var directionsDocuments = trackDocument.getJsonArray("directions");
        final var accelerations = new ArrayList<>(point3D(accelerationsDocuments));
        final var rotations = new ArrayList<>(point3D(rotationsDocuments));
        final var directions = new ArrayList<>(point3D(directionsDocuments));

        final var track = new Track(locationRecords, accelerations, rotations, directions);
        return new TrackBucket(trackId, bucket, track, metaData);
    }
}
