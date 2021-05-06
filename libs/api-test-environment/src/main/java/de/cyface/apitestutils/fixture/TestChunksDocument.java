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
 * A data chunk of a test document. This is how mongo stores large binary objects
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
final class TestChunksDocument implements MongoTestData {
    /**
     * The database wide unique identifier of the file.
     */
    private final String filesId;
    /**
     * The binary data represented by this chunk.
     */
    private final String binary;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param filesId The database wide unique identifier of the file.
     * @param binary The binary data represented by this chunk.
     */
    public TestChunksDocument(final String filesId, final String binary) {
        Validate.notEmpty(filesId);
        Validate.notEmpty(binary);

        this.filesId = filesId;
        this.binary = binary;
    }

    @Override
    public void insert(final MongoClient mongoClient, final Handler<AsyncResult<Void>> resultHandler) {

        final JsonObject chunksDocument = new JsonObject()
                .put(DatabaseConstants.CHUNKS_FILES_ID_FIELD, new JsonObject().put("$oid", filesId))
                .put(DatabaseConstants.CHUNKS_DATA_FIELD,
                        new JsonObject().put(DatabaseConstants.CHUNKS_DATA_BINARY_FIELD, binary));

        mongoClient.insert(DatabaseConstants.COLLECTION_CHUNKS, chunksDocument, result -> {
            if (result.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture(result.cause()));
            }
        });
    }
}
