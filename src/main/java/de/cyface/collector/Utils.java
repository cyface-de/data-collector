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
