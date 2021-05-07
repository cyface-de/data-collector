/*
 * Copyright 2018, 2019 Cyface GmbH
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

import java.io.File;
import java.util.HashSet;
import java.util.stream.Collectors;

import de.cyface.api.RequestHandler;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cyface.api.ServerConfig;
import de.cyface.collector.model.GeoLocation;
import de.cyface.collector.model.Measurement;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.mongo.GridFsUploadOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for receiving HTTP POST requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.4
 * @since 2.0.0
 */
@SuppressWarnings("PMD.AvoidCatchingNPE")
public final class MeasurementHandler extends RequestHandler {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);

    /**
     * Vertx <code>MongoClient</code> used to access the database to write the received data to.
     */
    private final MongoClient dataClient;

    /**
     * Creates a new, completely initialized instance of this class.
     *
     * @param serverConfig the configuration setting required to start the HTTP server
     */
    public MeasurementHandler(final ServerConfig serverConfig) {
        this(serverConfig.getDataDatabase(), serverConfig.getAuthProvider());
    }

    /**
     * @param dataClient The client to use to access the Mongo database.
     */
    public MeasurementHandler(final MongoClient dataClient, final MongoAuthentication authProvider) {
        super(authProvider);
        Validate.notNull(dataClient);
        this.dataClient = dataClient;
    }

    @Override
    protected void handleAuthorizedRequest(RoutingContext ctx) {
        LOGGER.info("Received new measurement request.");
        final var request = ctx.request();
        LOGGER.debug("FormAttributes: {}", request.formAttributes());
        final var user = ctx.user();

        try {
            GeoLocation startLocation = null;
            GeoLocation endLocation = null;
            final var locationCount = Long
                    .parseLong(request.getFormAttribute(FormAttributes.LOCATION_COUNT.getValue()));
            if (locationCount > 0) {
                final var startLocationLatString = request
                        .getFormAttribute(FormAttributes.START_LOCATION_LAT.getValue());
                final var startLocationLonString = request
                        .getFormAttribute(FormAttributes.START_LOCATION_LON.getValue());
                final var startLocationTsString = request
                        .getFormAttribute(FormAttributes.START_LOCATION_TS.getValue());
                final var endLocationLatString = request
                        .getFormAttribute(FormAttributes.END_LOCATION_LAT.getValue());
                final var endLocationLonString = request
                        .getFormAttribute(FormAttributes.END_LOCATION_LON.getValue());
                final var endLocationTsString = request.getFormAttribute(FormAttributes.END_LOCATION_TS.getValue());

                if (startLocationLatString == null || startLocationLonString == null || startLocationTsString == null
                        || endLocationLatString == null || endLocationLonString == null
                        || endLocationTsString == null) {
                    LOGGER.error("Data incomplete!");
                    ctx.fail(422);
                    return;
                } else {

                    final var startLocationLat = Double.parseDouble(startLocationLatString);
                    final var startLocationLon = Double.parseDouble(startLocationLonString);
                    final var startLocationTs = Long.parseLong(startLocationTsString);
                    final var endLocationLat = Double.parseDouble(endLocationLatString);
                    final var endLocationLon = Double.parseDouble(endLocationLonString);
                    final var endLocationTs = Long.parseLong(endLocationTsString);

                    startLocation = new GeoLocation(startLocationLat, startLocationLon, startLocationTs);
                    endLocation = new GeoLocation(endLocationLat, endLocationLon, endLocationTs);
                }
            }
            final var deviceId = request.getFormAttribute(FormAttributes.DEVICE_ID.getValue());
            final var deviceType = request.getFormAttribute(FormAttributes.DEVICE_TYPE.getValue());
            final var measurementId = request.getFormAttribute(FormAttributes.MEASUREMENT_ID.getValue());
            final var osVersion = request.getFormAttribute(FormAttributes.OS_VERSION.getValue());
            final var applicationVersion = request.getFormAttribute(FormAttributes.APPLICATION_VERSION.getValue());
            final var length = Double.parseDouble(request.getFormAttribute(FormAttributes.LENGTH.getValue()));
            final var vehicleType = request.getFormAttribute(FormAttributes.VEHICLE_TYPE.getValue());
            final var username = user.principal().getString("username");

            final var uploads = new HashSet<Measurement.FileUpload>();
            ctx.fileUploads()
                    .forEach(upload -> uploads.add(new Measurement.FileUpload(new File(upload.uploadedFileName()),
                            FilenameUtils.getExtension(upload.fileName()))));

            storeToMongoDB(new Measurement(deviceId, measurementId, osVersion, deviceType, applicationVersion,
                    length, locationCount, startLocation, endLocation, vehicleType, username, uploads), ctx);

        } catch (final IllegalArgumentException | NullPointerException e) {
            LOGGER.error("Data was not parsable!", e);
            ctx.fail(422);
        }

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

        final var gridFsBucketCreationFuture = dataClient.createDefaultGridFsBucketService();

        gridFsBucketCreationFuture.onSuccess(gridFsClient -> {
            final var fileSystem = ctx.vertx().fileSystem();
            final var fileUploadFutures = measurement.getFileUploads().stream().map(fileUpload -> {
                final var fileOpenFuture = fileSystem.open(fileUpload.getFile().getAbsolutePath(),
                        new OpenOptions());
                final var uploadFuture = fileOpenFuture.compose(asyncFile -> {
                    final var measurementJson = measurement.toJson();
                    measurementJson.put("fileType", fileUpload.getFileType());

                    final var gridFsOptions = new GridFsUploadOptions();
                    gridFsOptions.setMetadata(new JsonObject(measurementJson.toString()));

                    return gridFsClient.uploadByFileNameWithOptions(asyncFile,
                            fileUpload.getFile().getName(), gridFsOptions);
                });
                return (Future)uploadFuture;
            }).collect(Collectors.toList());

            CompositeFuture.all(fileUploadFutures).onSuccess(result -> ctx.response().setStatusCode(201).end())
                    .onFailure(cause -> {
                        LOGGER.error("Unable to store file to MongoDatabase!", cause);
                        ctx.fail(422);
                    });
        }).onFailure(cause -> {
            LOGGER.error("Unable to open connection to Mongo Database!", cause);
            ctx.fail(422);
        });
    }
}
