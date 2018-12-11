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

import static de.cyface.collector.EventBusAddresses.MEASUREMENT_SAVED;
import static de.cyface.collector.EventBusAddresses.NEW_MEASUREMENT;
import static de.cyface.collector.EventBusAddresses.SAVING_MEASUREMENT_FAILED;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.mongodb.ConnectionString;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.async.client.gridfs.AsyncInputStream;
import com.mongodb.async.client.gridfs.GridFSBucket;
import com.mongodb.async.client.gridfs.GridFSBuckets;

import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

/**
 * This <code>Verticle</code> listens for new measurements arriving in the
 * system and stores the to the MongoDB for persistent storage.
 * 
 * @author Klemens Muthmann
 * @version 1.1.1
 * @since 2.0.0
 */
public final class SerializationVerticle extends AbstractVerticle implements Handler<Message<Measurement>> {
    /**
     * The <code>Logger</code> used for objects of this class. To configure it
     * change the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SerializationVerticle.class);
    /**
     * Client wrapper for calls to the MongoDB used to store the measurement data.
     */
    private MongoClient mongoClient;

    @Override
    public void start() throws Exception {
        super.start();

        mongoClient = createSharedMongoClient(vertx, config());

        registerForNewMeasurements();
    }

    /**
     * Register this <code>Verticle</code> to receive messages about new
     * measurements arriving at the system.
     */
    private void registerForNewMeasurements() {
        EventBus eventBus = vertx.eventBus();
        eventBus.consumer(NEW_MEASUREMENT, this);
    }

    @Override
    public void handle(Message<Measurement> event) {
        Measurement measurement = event.body();
        LOGGER.debug("About to save: " + measurement.getFileUploads().size() + " Files.");
        JsonObject measurementJson = measurement.toJson();
        SerializationHandler serializationHandler = new SerializationHandler(measurement);

        mongoClient.insert("measurements", measurementJson, serializationHandler);
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

    /**
     * A handler called when a measurement has been created in the Mongo database.
     * This handler saves the measurement data and send a message to
     * {@link EventBusAddresses#MEASUREMENT_SAVED} upon success or
     * {@link EventBusAddresses#SAVING_MEASUREMENT_FAILED} on failure.
     * 
     * @author Klemens Muthmann
     * @version 1.0.0
     * @since 2.0.0
     */
    class SerializationHandler implements Handler<AsyncResult<String>> {

        /**
         * The measurement that was saved.
         */
        private final Measurement measurement;

        /**
         * Creates a new completely initialized handler.
         * 
         * @param measurement The measurement that was saved.
         */
        public SerializationHandler(final Measurement measurement) {
            Validate.notNull(measurement);
            this.measurement = measurement;
        }

        @Override
        public void handle(AsyncResult<String> res) {
            if (res.succeeded()) {
                String id = res.result();
                LOGGER.debug("Inserted measurement with id " + id);

                // Store to Mongo GridFs
                final String mongoConnectionString = config().getString("connection_string");
                final String mongoDatabaseName = config().getString("db_name");
                MongoDatabase db = com.mongodb.async.client.MongoClients
                        .create(new ConnectionString(mongoConnectionString)).getDatabase(mongoDatabaseName);
                GridFSBucket gridFsBucket = GridFSBuckets.create(db);

                Collection<File> filesToUpload = measurement.getFileUploads();
                @SuppressWarnings("rawtypes")
                List<Future> fileUploadFutures = new ArrayList<>(filesToUpload.size());

                filesToUpload.forEach(upload -> {

                    Future<String> future = Future.future();
                    try {
                        fileUploadFutures.add(future);
                        FileInputStream fileInputStream = new FileInputStream(upload.getAbsolutePath());
                        AsyncInputStream asyncStream = com.mongodb.async.client.gridfs.helpers.AsyncStreamHelper
                                .toAsyncInputStream(fileInputStream);
                        gridFsBucket.uploadFromStream(upload.getName(), asyncStream, (result, throwable) -> {
                            LOGGER.debug("Saved file as object " + result);
                            future.complete();
                        });
                    } catch (FileNotFoundException e) {
                        LOGGER.error("Error during serialization.", e);
                        future.fail(e);
                    }
                });

                CompositeFuture.all(fileUploadFutures).setHandler(result -> {
                    if (result.succeeded()) {
                        vertx.eventBus().publish(MEASUREMENT_SAVED, id);
                    } else {
                        LOGGER.error("Error during serialization.", res.cause());
                        fail(res);
                    }
                });
            } else {
                LOGGER.error("Error during serialization.", res.cause());
                fail(res);
            }

        }

        /**
         * Fails saving the measurement by sending an appropriate message over the event
         * bus.
         * 
         * @param res The result to fail for. This contains further information about
         *            the reason the serialization failed.
         * @see EventBusAddresses#SAVING_MEASUREMENT_FAILED
         */
        private void fail(final AsyncResult<String> res) {
            LOGGER.error("Unable to save measurement with id {}", res.cause(), measurement.getMeasurementIdentifier());
            vertx.eventBus().publish(SAVING_MEASUREMENT_FAILED, res.cause().getMessage());
        }

    }

}
