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
package de.cyface.collector.verticle;

import static de.cyface.collector.EventBusAddressUtils.MEASUREMENT_SAVED;
import static de.cyface.collector.EventBusAddressUtils.NEW_MEASUREMENT;
import static de.cyface.collector.EventBusAddressUtils.SAVING_MEASUREMENT_FAILED;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.bson.Document;

import com.mongodb.ConnectionString;
import com.mongodb.async.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import de.cyface.collector.EventBusAddressUtils;
import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This <code>Verticle</code> listens for new measurements arriving in the system and stores them to the MongoDB for
 * persistent storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.1.5
 * @since 2.0.0
 */
public final class SerializationVerticle extends AbstractVerticle implements Handler<Message<Measurement>> {
    /**
     * The <code>Logger</code> used for objects of this class. To configure it change the settings in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SerializationVerticle.class);
    /**
     * The default url to use to connect to a Mongo database if none has been provided via Verticle configuration.
     */
    private static final String DEFAULT_MONGO_URL = "mongodb://localhost:27017";

    @Override
    public void start() throws Exception {
        super.start();

        registerForNewMeasurements();
    }

    /**
     * Register this <code>Verticle</code> to receive messages about new measurements arriving at the system.
     */
    private void registerForNewMeasurements() {
        final var eventBus = vertx.eventBus();
        eventBus.consumer(NEW_MEASUREMENT, this);
    }

    @Override
    public void handle(final Message<Measurement> event) {
        final var measurement = event.body();
        LOGGER.debug("Inserted measurement with id {}:{}!", measurement.getDeviceIdentifier(),
                measurement.getMeasurementIdentifier());

        // Store to Mongo GridFs
        final var mongoConnectionString = config().getString("connection_string", DEFAULT_MONGO_URL);
        final var mongoDatabaseName = config().getString("db_name", "test");
        final var database = com.mongodb.async.client.MongoClients
                .create(new ConnectionString(mongoConnectionString)).getDatabase(mongoDatabaseName);
        final var gridFsBucket = GridFSBuckets.create(database);

        final var filesToUpload = measurement.getFileUploads();
        @SuppressWarnings("rawtypes")
        final var fileUploadFutures = new ArrayList<Future>(filesToUpload.size());

        LOGGER.debug("About to save: {} Files.", measurement.getFileUploads().size());
        filesToUpload.forEach(upload -> {

            final var future = Future.future();
            try {
                fileUploadFutures.add(future);
                final var fileInputStream = Files.newInputStream(Paths.get(upload.getFile().getAbsolutePath()));
                final var asyncStream = com.mongodb.async.client.gridfs.helpers.AsyncStreamHelper
                        .toAsyncInputStream(fileInputStream);

                final var measurementJson = measurement.toJson();
                measurementJson.put("fileType", upload.getFileType());

                final var options = new GridFSUploadOptions()
                        .metadata(Document.parse(measurementJson.toString()));

                gridFsBucket.uploadFromStream(upload.getFile().getName(), asyncStream, options, (result, throwable) -> {
                    LOGGER.debug("Saved file as object {}", result);
                    future.complete();
                });
            } catch (IOException e) {
                LOGGER.error("Error during serialization.", e);
                future.fail(e);
            }
        });

        CompositeFuture.all(fileUploadFutures).setHandler(result -> {
            if (result.succeeded()) {
                vertx.eventBus().publish(MEASUREMENT_SAVED, String.format("%s:%s", measurement.getDeviceIdentifier(),
                        measurement.getMeasurementIdentifier()));
            } else {
                fail(result.cause(), measurement);
            }
        });
    }

    /**
     * Fails saving the measurement by sending an appropriate message over the event bus.
     *
     * @param res The <code>Throwable</code> causing the failure. This contains further information about the reason
     *            the serialization failed.
     * @param measurement The measurement for which synchronization failed.
     * @see EventBusAddressUtils#SAVING_MEASUREMENT_FAILED
     */
    private void fail(final Throwable res, final Measurement measurement) {
        LOGGER.error("Unable to save measurement with id {}", res, measurement.getMeasurementIdentifier());
        vertx.eventBus().publish(SAVING_MEASUREMENT_FAILED, res.getMessage());
    }
}
