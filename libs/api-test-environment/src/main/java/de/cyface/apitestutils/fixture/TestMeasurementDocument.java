/*
 * Copyright 2020-2022 Cyface GmbH
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
package de.cyface.apitestutils.fixture;

import static de.cyface.apitestutils.fixture.DatabaseConstants.DESERIALIZED_TRACK_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_APP_VERSION_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_DEVICE_ID_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_DEVICE_TYPE_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_LENGTH_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_MEASUREMENT_ID_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_OS_VERSION_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.METADATA_VERSION_FIELD;
import static de.cyface.apitestutils.fixture.DatabaseConstants.USER_ID_FIELD;

import org.apache.commons.lang3.Validate;

import de.cyface.model.Modality;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * A test document inside the Mongo database which contains an unpacked (deserialized) measurement.
 *
 * @author Armin Schnabel
 * @version 3.0.0
 * @since 1.2.0
 */
public final class TestMeasurementDocument implements MongoTestData {

    /**
     * The id of the user who uploaded the file.
     */
    private final String ownerUserId;
    /**
     * The identifier of the measurement encoded in the file.
     */
    private final long measurementIdentifier;
    /**
     * The worldwide unique identifier of the device the document comes from.
     */
    private final String deviceIdentifier;
    /**
     * The database collection name which contains deserialized measurements.
     */
    private static final String COLLECTION_DESERIALIZED = "deserialized";

    /**
     * Creates a new completely initialized document. You may insert it into a Mongo database by calling
     * {@link #insert(MongoClient)}.
     *
     * @param ownerUserId The id of the user who uploaded the file.
     * @param measurementIdentifier The identifier of the measurement encoded in the file
     * @param deviceIdentifier The worldwide unique identifier of the device the document comes from
     */
    public TestMeasurementDocument(final String ownerUserId, final Long measurementIdentifier,
            final String deviceIdentifier) {
        Validate.notEmpty(ownerUserId);
        Validate.notNull(measurementIdentifier);
        Validate.notEmpty(deviceIdentifier);

        this.ownerUserId = ownerUserId;
        this.measurementIdentifier = measurementIdentifier;
        this.deviceIdentifier = deviceIdentifier;
    }

    @Override
    public Future<String> insert(final MongoClient mongoClient) {

        final var metaData = new JsonObject()
                .put(METADATA_DEVICE_ID_FIELD, deviceIdentifier)
                .put(METADATA_MEASUREMENT_ID_FIELD, measurementIdentifier)
                .put(METADATA_DEVICE_TYPE_FIELD, "Pixel 3")
                .put(METADATA_OS_VERSION_FIELD, "Android 9.0.0")
                .put(METADATA_APP_VERSION_FIELD, "1.2.0")
                .put(METADATA_LENGTH_FIELD, 1500.2)
                .put(USER_ID_FIELD, ownerUserId)
                .put(METADATA_VERSION_FIELD, "2.0.0");

        final JsonArray geoLocations = new JsonArray();
        final var geometry1 = new JsonObject()
                .put("type", "Point")
                .put("coordinates", new JsonArray().add(13.1).add(51.1));
        final JsonObject geoLocation = new JsonObject()
                .put("geometry", geometry1)
                .put("timestamp", 1L)
                .putNull("elevation")
                .put("speed", 5.0)
                .put("accuracy", 0.13)
                .put("modality", Modality.UNKNOWN.getDatabaseIdentifier());
        final var geometry2 = new JsonObject()
                .put("type", "Point")
                .put("coordinates", new JsonArray().add(13.2).add(51.2));
        final JsonObject geoLocation2 = new JsonObject()
                .put("geometry", geometry2)
                .put("timestamp", 2L)
                .putNull("elevation")
                .put("speed", 5.0)
                .put("accuracy", 0.13)
                .put("modality", Modality.BICYCLE.getDatabaseIdentifier());
        geoLocations.add(geoLocation);
        geoLocations.add(geoLocation2);

        final JsonArray accelerations = new JsonArray();
        final JsonObject point3D = new JsonObject()
                .put("timestamp", 1L)
                .put("x", 5.0)
                .put("y", -5.0)
                .put("z", 0.0);
        final JsonObject point3D2 = new JsonObject()
                .put("timestamp", 2L)
                .put("x", 5.0)
                .put("y", -5.0)
                .put("z", 0.0);
        accelerations.add(point3D);
        accelerations.add(point3D2);
        final JsonArray rotations = new JsonArray();
        rotations.add(point3D);
        final JsonArray directions = new JsonArray();
        directions.add(point3D2);

        final JsonObject trackBucket = new JsonObject()
                .put("trackId", 0)
                .put("bucket", new JsonObject().put("$date", "2020-12-22T15:13:00.000+00:00"))
                .put("geoLocations", geoLocations)
                .put("accelerations", accelerations)
                .put("rotations", rotations)
                .put("directions", directions);

        final var measurementDocument = new JsonObject()
                .put(METADATA_FIELD, metaData)
                .put(DESERIALIZED_TRACK_FIELD, trackBucket);

        final Promise<String> promise = Promise.promise();
        final var insert = mongoClient.insert(COLLECTION_DESERIALIZED, measurementDocument);
        insert.onSuccess(promise::complete);
        insert.onFailure(promise::fail);
        return promise.future();
    }
}
