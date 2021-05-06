/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.apitestutils.fixture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.mongo.MongoClient;

/**
 * Some data that can be inserted as test data into the Cyface Mongo database.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
interface MongoTestData {
    /**
     * Inserts the data via the provided client and calls the provided <code>resultHandler</code> after completion.
     *
     * @param mongoClient The client to use to insert the data into the Mongo database
     * @param resultHandler The handler to call after insertion has completed. The provided result contains information
     *            about whether the data was inserted successfully or not.
     */
    void insert(final MongoClient mongoClient, final Handler<AsyncResult<Void>> resultHandler);
}
