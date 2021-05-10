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
