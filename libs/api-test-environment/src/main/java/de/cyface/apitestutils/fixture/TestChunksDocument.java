/*
 * Copyright 2020-2021 Cyface GmbH
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
