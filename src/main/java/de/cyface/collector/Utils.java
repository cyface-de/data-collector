/*
 * Copyright 2018 Cyface GmbH
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
package de.cyface.collector;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.HashAlgorithm;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;

/**
 * This class provides basic static utility methods, used throughout the system.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class Utils {

	/**
	 * Private constructor to prevent instantiation of static utility class.
	 */
	private Utils() {
		// Nothing to do here.
	}
	
	/**
     * @param client A Mongo client to access the user Mongo database.
     * @return Authentication provider used to check for valid user accounts used to generate new JWT
     *         token.
     */
    public static MongoAuth buildMongoAuthProvider(final MongoClient client) {
        final JsonObject authProperties = new JsonObject();
        final MongoAuth authProvider = MongoAuth.create(client, authProperties);
        authProvider.setHashAlgorithm(HashAlgorithm.SHA512);

        return authProvider;
    }
    
    /**
     * Creates a shared Mongo database client for the provided configuration.
     * 
     * @param vertx The <code>Vertx</code> instance to create the client from.
     * @param config Configuration of the newly created client. For further
     *            information refer to {@link Parameter#MONGO_DATA_DB} and
     *            {@link Parameter#MONGO_USER_DB}.
     * @return A <code>MongoClient</code> ready for usage.
     */
    public static MongoClient createSharedMongoClient(final Vertx vertx, final JsonObject config) {
        final String dataSourceName = config.getString("data_source_name", "cyface");
        return MongoClient.createShared(vertx, config, dataSourceName);
    }

}
