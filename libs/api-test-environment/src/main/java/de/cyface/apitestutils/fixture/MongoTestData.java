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

import io.vertx.core.Future;
import io.vertx.ext.mongo.MongoClient;

/**
 * Some data that can be inserted as test data into the Cyface Mongo database.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 1.0.0
 */
public interface MongoTestData {

    /**
     * Inserts the data via the provided client and calls the provided <code>resultHandler</code> after completion.
     *
     * @param mongoClient The client to use to insert the data into the Mongo database
     * @return a {@code Future<String>} which resolves to the id of the created entry if successful.
     */
    Future<String> insert(final MongoClient mongoClient);
}
