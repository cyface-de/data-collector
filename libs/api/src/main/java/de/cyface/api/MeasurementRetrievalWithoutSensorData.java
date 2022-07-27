/*
 * Copyright 2021-2022 Cyface GmbH
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import de.cyface.model.Track;
import de.cyface.model.TrackBucket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;

// TODO: Probably remove this, as it is not necessary for data collection
/**
 * This implementation of the {@link MeasurementRetrievalStrategy} only loads the {@link Track} data from the database
 * when retrieving measurements. I.e. the sensor data lists attached in the tracks will be empty to reduce load.
 * <p>
 * If you need the sensor data, use {@link MeasurementRetrievalWithSensorData} instead.
 *
 * @author Armin Schnabel
 * @since 6.0.0
 * @version 1.0.0
 */
public class MeasurementRetrievalWithoutSensorData implements MeasurementRetrievalStrategy {

    @Override
    public FindOptions findOptions() {
        final var fields = new JsonObject()
                .put("track.accelerations", 0)
                .put("track.rotations", 0)
                .put("track.directions", 0);
        // Ensure the measurements are returned in order (or else we have flaky tests)
        final var sort = new JsonObject().put("metaData.deviceId", 1).put("metaData.measurementId", 1);
        return new FindOptions().setFields(fields).setSort(sort);
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

        final var track = new Track(locationRecords, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        // noinspection SpellCheckingInspection
        final var date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(bucket.getString("$date"));
        return new TrackBucket(trackId, date, track, metaData);
    }
}
