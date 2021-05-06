/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import org.apache.commons.lang3.Validate;

import de.cyface.model.Modality;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * A test document inside the Mongo database which contains an unpacked (deserialized) measurement.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.2.0
 */
final class TestMeasurementDocument implements MongoTestData {
    /**
     * The username who uploaded the file.
     */
    private final String ownerUsername;
    /**
     * The identifier of the measurement encoded in the file.
     */
    private final long measurementIdentifier;
    /**
     * The world wide unique identifier of the device the document comes from.
     */
    private final String deviceIdentifier;
    /**
     * The database collection name which contains deserialized measurements.
     */
    private static final String COLLECTION_DESERIALIZED = "deserialized";

    /**
     * Creates a new completely initialized document. You may insert it into a Mongo database by calling
     * {@link #insert(MongoClient, Handler)}.
     *
     * @param ownerUsername The username who uploaded the file
     * @param measurementIdentifier The identifier of the measurement encoded in the file
     * @param deviceIdentifier The world wide unique identifier of the device the document comes from
     */
    public TestMeasurementDocument(final String ownerUsername, final Long measurementIdentifier,
            final String deviceIdentifier) {
        Validate.notEmpty(ownerUsername);
        Validate.notNull(measurementIdentifier);
        Validate.notEmpty(deviceIdentifier);

        this.ownerUsername = ownerUsername;
        this.measurementIdentifier = measurementIdentifier;
        this.deviceIdentifier = deviceIdentifier;
    }

    @Override
    public void insert(final MongoClient mongoClient, final Handler<AsyncResult<Void>> resultHandler) {

        final JsonObject metaData = new JsonObject()
                .put("deviceId", deviceIdentifier)
                .put("measurementId", measurementIdentifier)
                .put("deviceType", "Pixel 3")
                .put("osVersion", "Android 9.0.0")
                .put("appVersion", "1.2.0")
                .put("length", 1500.2)
                .put("username", ownerUsername)
                .put("version", "1.0.0");

        final JsonArray geoLocations = new JsonArray();
        final JsonObject geoLocation = new JsonObject()
                .put("timestamp", 1L)
                .put("latitude", 51.1)
                .put("longitude", 13.1)
                .putNull("elevation")
                .put("speed", 5.0)
                .put("accuracy", 0.13)
                .put("modality", Modality.UNKNOWN.getDatabaseIdentifier());
        final JsonObject geoLocation2 = new JsonObject()
                .put("timestamp", 2L)
                .put("latitude", 51.2)
                .put("longitude", 13.2)
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

        final JsonObject measurementDocument = new JsonObject()
                .put("metaData", metaData)
                .put("track", trackBucket);

        mongoClient.insert(COLLECTION_DESERIALIZED, measurementDocument, result -> {
            if (result.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                // Make the test fail
                resultHandler.handle(Future.failedFuture(result.cause()));
            }
        });
    }
}
