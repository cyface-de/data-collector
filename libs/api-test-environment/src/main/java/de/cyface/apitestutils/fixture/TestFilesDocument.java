/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import org.apache.commons.lang3.Validate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * A test document inside the Mongo database. In Mongo a document consists of this file and chunks containing the
 * actual data.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
final class TestFilesDocument implements MongoTestData {
    /**
     * The username who uploaded the file.
     */
    private final String ownerUsername;
    /**
     * The type of the file, which is either a file in the Cyface binary format (ccyf) or an events file (ccyfe).
     */
    private final String fileType;
    /**
     * The Mongo identifier for the file.
     */
    private final String filesId;
    /**
     * The identifier of the measurement encoded in the file.
     */
    private final String measurementIdentifier;
    /**
     * The world wide unique identifier of the device the document comes from.
     */
    private final String deviceIdentifier;

    /**
     * Creates a new completely initialized document. You may insert it into a Mongo database by calling
     * {@link #insert(MongoClient, Handler)}.
     *
     * @param ownerUsername The username who uploaded the file
     * @param fileType The type of the file, which is either a file in the Cyface binary format (ccyf) or an events
     *            file (ccyfe)
     * @param filesId The Mongo identifier for the file
     * @param measurementIdentifier The identifier of the measurement encoded in the file
     * @param deviceIdentifier The world wide unique identifier of the device the document comes from
     */
    public TestFilesDocument(final String ownerUsername, final String fileType, final String filesId,
            final String measurementIdentifier, final String deviceIdentifier) {
        Validate.notEmpty(ownerUsername);
        Validate.notEmpty(fileType);
        Validate.notEmpty(filesId);
        Validate.notEmpty(measurementIdentifier);
        Validate.notEmpty(deviceIdentifier);

        this.ownerUsername = ownerUsername;
        this.fileType = fileType;
        this.filesId = filesId;
        this.measurementIdentifier = measurementIdentifier;
        this.deviceIdentifier = deviceIdentifier;
    }

    @Override
    public void insert(final MongoClient mongoClient, final Handler<AsyncResult<Void>> resultHandler) {

        final JsonObject metaData = new JsonObject()
                .put(DatabaseConstants.METADATA_DEVICE_ID_FIELD, deviceIdentifier)
                .put(DatabaseConstants.METADATA_MEASUREMENT_ID_FIELD, measurementIdentifier)
                .put(DatabaseConstants.USER_USERNAME_FIELD, ownerUsername)
                .put(DatabaseConstants.METADATA_FILE_TYPE_FIELD, fileType);
        final JsonObject filesDocument = new JsonObject()
                .put(DatabaseConstants.ID_FIELD, new JsonObject().put("$oid", filesId))
                .put(DatabaseConstants.METADATA_FIELD, metaData);

        mongoClient.insert(DatabaseConstants.COLLECTION_FILES, filesDocument, result -> {
            if (result.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                // Make the test fail
                resultHandler.handle(Future.failedFuture(result.cause()));
            }
        });
    }
}
