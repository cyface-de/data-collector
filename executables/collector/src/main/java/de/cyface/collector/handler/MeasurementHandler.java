/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.collector.handler;

import static de.cyface.api.MongoAuthHandler.ENTITY_UNPARSABLE;
import static de.cyface.collector.handler.PreRequestHandler.MINIMUM_LOCATION_COUNT;
import static de.cyface.collector.handler.PreRequestHandler.PRECONDITION_FAILED;
import static de.cyface.collector.handler.PreRequestHandler.checkUploadSize;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.collector.handler.exception.InvalidMetaData;
import de.cyface.collector.handler.exception.PayloadTooLarge;
import de.cyface.collector.handler.exception.SkipUpload;
import de.cyface.collector.handler.exception.Unparsable;
import de.cyface.collector.model.GeoLocation;
import de.cyface.collector.model.Measurement;
import io.vertx.core.Handler;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.GridFsUploadOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for receiving HTTP PUT requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
@SuppressWarnings("PMD.AvoidCatchingNPE")
public final class MeasurementHandler implements Handler<RoutingContext> {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);
    /**
     * HTTP status code to return when the client tries to upload too much data.
     */
    static final int PAYLOAD_TOO_LARGE = 413;
    /**
     * Vertx <code>MongoClient</code> used to access the database to write the received data to.
     */
    private final MongoClient dataClient;
    /**
     * The maximum number of {@code Byte}s which may be uploaded.
     */
    private final long payloadLimit;

    /**
     * Handler which cleans up uploaded files after they are persisted or after an upload attempt failed.
     */
    private final static Handler<File> cleanupHandler = file -> {
        final var deleted = file.delete();
        Validate.isTrue(deleted);
    };

    /**
     * @param dataClient The client to use to access the Mongo database.
     * @param payloadLimit The maximum number of {@code Byte}s which may be uploaded.
     */
    public MeasurementHandler(final MongoClient dataClient, final long payloadLimit) {
        Validate.notNull(dataClient);
        Validate.isTrue(payloadLimit > 0);
        this.dataClient = dataClient;
        this.payloadLimit = payloadLimit;
    }

    @Override
    public void handle(RoutingContext ctx) {
        LOGGER.info("Received new measurement request.");
        final var request = ctx.request();
        final var user = ctx.user();
        try {
            checkUploadSize(request.headers(), payloadLimit, "content-length");
        } catch (Unparsable e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(ENTITY_UNPARSABLE);
            return;
        } catch (PayloadTooLarge e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(PAYLOAD_TOO_LARGE, e.getCause());
            return;
        }

        // Pipe body as steam (reduce memory usage & store body of interrupted connections when supporting resume)
        final var fileName = UUID.randomUUID().toString();
        final var uploadFolder = Path.of("file-uploads/").toFile();
        if (!uploadFolder.exists()) {
            Validate.isTrue(uploadFolder.mkdir());
        }
        final var tempFile = Paths.get(uploadFolder.getPath(), fileName).toAbsolutePath().toFile();

        request.pause();
        ctx.vertx().fileSystem().open(tempFile.getAbsoluteFile().toString(), new OpenOptions().setAppend(true), fileOpenResult -> {
            request.resume();

            if (fileOpenResult.failed()) {
                LOGGER.error("Unable to open temporary file to stream request to!", fileOpenResult.cause());
                ctx.fail(500, fileOpenResult.cause());
                return;
            }

            // Streaming (pipeTo) the body does not work in the test, if the Google API client lib is not used
            // (SocketTimeout in CI)
            final var writeStream = fileOpenResult.result();
            final var pipeFuture = request.pipeTo(writeStream);
            pipeFuture.onSuccess(success -> {
                try {
                    // Persist data
                    final var username = user.principal().getString("username");
                    final var measurement = measurement(ctx.request(), tempFile, username);
                    storeToMongoDB(measurement, ctx);
                } catch (InvalidMetaData e) {
                    LOGGER.error(e.getMessage(), e);
                    cleanupHandler.handle(tempFile);
                    ctx.fail(ENTITY_UNPARSABLE);
                } catch (SkipUpload e) {
                    LOGGER.debug(e.getMessage(), e);
                    cleanupHandler.handle(tempFile);
                    ctx.fail(PRECONDITION_FAILED);
                }
            });
            pipeFuture.onFailure(failure -> {
                cleanupHandler.handle(tempFile);
                if (failure.getClass().equals(PayloadTooLarge.class)) {
                    LOGGER.error(failure.getMessage(), failure);
                    ctx.fail(PAYLOAD_TOO_LARGE, failure.getCause());
                    return;
                }
                ctx.fail(500, failure);
            });
        });
    }

    /**
     * Stores a {@link Measurement} to a Mongo database. This method never fails. If a failure occurs it is logged and
     * status code 422 is used for the response.
     *
     * @param measurement The measured data to write to the Mongo database
     * @param ctx The Vertx <code>RoutingContext</code> used to write the response
     */
    public void storeToMongoDB(final Measurement measurement, final RoutingContext ctx) {
        LOGGER.debug("Inserted measurement with id {}:{}!", measurement.getDeviceIdentifier(),
                measurement.getMeasurementIdentifier());

        final var access = dataClient.createDefaultGridFsBucketService();
        access.onSuccess(gridFs -> {

            final var fileSystem = ctx.vertx().fileSystem();
            final var fileUpload = measurement.getBinary();
            final var openFuture = fileSystem.open(fileUpload.getAbsolutePath(), new OpenOptions());
            final var uploadFuture = openFuture.compose(file -> {

                final var options = new GridFsUploadOptions();
                final var metaData = measurement.toJson();
                options.setMetadata(new JsonObject(metaData.toString()));

                return gridFs.uploadByFileNameWithOptions(file, fileUpload.getName(), options);
            });

            // Wait for all file uploads to complete
            uploadFuture
                    .onComplete(result -> cleanupHandler.handle(measurement.getBinary()))
                    .onSuccess(result -> ctx.response().setStatusCode(201).end())
                    .onFailure(cause -> {
                        LOGGER.error("Unable to store file to MongoDatabase!", cause);
                        ctx.fail(500, cause);
                    });

        }).onFailure(cause -> {
            LOGGER.error("Unable to open connection to Mongo Database!", cause);
            cleanupHandler.handle(measurement.getBinary());
            ctx.fail(500, cause);
        });
    }

    /**
     * Creates a {@link Measurement} from an {@code HttpServerRequest}.
     *
     * @param request the request to create the measurement from.
     * @param binary the uploaded binary
     * @param username the name of the measurement owner.
     * @return the created measurement
     * @throws SkipUpload when the server is not interested in the uploaded data
     * @throws InvalidMetaData when the request is missing metadata fields
     */
    private Measurement measurement(final HttpServerRequest request, final File binary, final String username)
            throws SkipUpload, InvalidMetaData {
        try {
            final var locationCount = Long
                    .parseLong(request.getHeader(FormAttributes.LOCATION_COUNT.getValue()));
            if (locationCount < MINIMUM_LOCATION_COUNT) {
                throw new SkipUpload(String.format("Too few location points %s", locationCount));
            }

            final var startLocationLatString = request
                    .getHeader(FormAttributes.START_LOCATION_LAT.getValue());
            final var startLocationLonString = request
                    .getHeader(FormAttributes.START_LOCATION_LON.getValue());
            final var startLocationTsString = request
                    .getHeader(FormAttributes.START_LOCATION_TS.getValue());
            final var endLocationLatString = request
                    .getHeader(FormAttributes.END_LOCATION_LAT.getValue());
            final var endLocationLonString = request
                    .getHeader(FormAttributes.END_LOCATION_LON.getValue());
            final var endLocationTsString = request.getHeader(FormAttributes.END_LOCATION_TS.getValue());

            if (startLocationLatString == null || startLocationLonString == null || startLocationTsString == null
                    || endLocationLatString == null || endLocationLonString == null) {
                throw new InvalidMetaData("Data incomplete!");
            }

            final var startLocationLat = Double.parseDouble(startLocationLatString);
            final var startLocationLon = Double.parseDouble(startLocationLonString);
            final var startLocationTs = Long.parseLong(startLocationTsString);
            final var endLocationLat = Double.parseDouble(endLocationLatString);
            final var endLocationLon = Double.parseDouble(endLocationLonString);
            final var endLocationTs = Long.parseLong(endLocationTsString);

            GeoLocation startLocation = new GeoLocation(startLocationLat, startLocationLon, startLocationTs);
            GeoLocation endLocation = new GeoLocation(endLocationLat, endLocationLon, endLocationTs);

            final var deviceId = request.getHeader(FormAttributes.DEVICE_ID.getValue());
            final var deviceType = request.getHeader(FormAttributes.DEVICE_TYPE.getValue());
            final var measurementId = request.getHeader(FormAttributes.MEASUREMENT_ID.getValue());
            final var osVersion = request.getHeader(FormAttributes.OS_VERSION.getValue());
            final var applicationVersion = request.getHeader(FormAttributes.APPLICATION_VERSION.getValue());
            final var length = Double.parseDouble(request.getHeader(FormAttributes.LENGTH.getValue()));
            final var vehicleType = request.getHeader(FormAttributes.VEHICLE_TYPE.getValue());

            final var formatVersionString = request.getHeader(FormAttributes.FORMAT_VERSION.getValue());
            if (formatVersionString == null) {
                throw new InvalidMetaData("Data incomplete!");
            }
            final var formatVersion = Integer.parseInt(formatVersionString);

            return new Measurement(
                    deviceId, measurementId, osVersion, deviceType, applicationVersion,
                    length, locationCount, startLocation, endLocation, vehicleType, username, binary, formatVersion);

        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new InvalidMetaData("Data was not parsable!", e);
        }
    }
}