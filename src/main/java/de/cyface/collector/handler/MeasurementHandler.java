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
package de.cyface.collector.handler;

import static de.cyface.collector.EventBusAddresses.NEW_MEASUREMENT;
import static de.cyface.collector.handler.FormAttributes.APPLICATION_VERSION;
import static de.cyface.collector.handler.FormAttributes.DEVICE_ID;
import static de.cyface.collector.handler.FormAttributes.DEVICE_TYPE;
import static de.cyface.collector.handler.FormAttributes.END_LOCATION_LAT;
import static de.cyface.collector.handler.FormAttributes.END_LOCATION_LON;
import static de.cyface.collector.handler.FormAttributes.END_LOCATION_TS;
import static de.cyface.collector.handler.FormAttributes.LENGTH;
import static de.cyface.collector.handler.FormAttributes.LOCATION_COUNT;
import static de.cyface.collector.handler.FormAttributes.MEASUREMENT_ID;
import static de.cyface.collector.handler.FormAttributes.OS_VERSION;
import static de.cyface.collector.handler.FormAttributes.START_LOCATION_LAT;
import static de.cyface.collector.handler.FormAttributes.START_LOCATION_LON;
import static de.cyface.collector.handler.FormAttributes.START_LOCATION_TS;
import static de.cyface.collector.handler.FormAttributes.VEHICLE_TYPE;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import de.cyface.collector.EventBusAddresses;
import de.cyface.collector.model.GeoLocation;
import de.cyface.collector.model.Measurement;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for receiving HTTP POST requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 * 
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 3.1.0
 * @since 2.0.0
 */
public final class MeasurementHandler implements Handler<RoutingContext> {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);

    /**
     * Maximum size of a meta data field, with plenty space for future development. This prevents attackers from putting
     * arbitrary long data into these fields.
     */
    private static final int MAX_GENERIC_METADATA_FIELD_LENGTH = 30;

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.info("Received new measurement request.");
        final HttpServerRequest request = ctx.request();
        final HttpServerResponse response = ctx.response();
        LOGGER.debug("FormAttributes: " + request.formAttributes());
        final User user = ctx.user();

        try {
            final String deviceId = request.getFormAttribute(DEVICE_ID.getValue());
            final String deviceType = request.getFormAttribute(DEVICE_TYPE.getValue());
            final String measurementId = request.getFormAttribute(MEASUREMENT_ID.getValue());
            final String osVersion = request.getFormAttribute(OS_VERSION.getValue());
            final String applicationVersion = request.getFormAttribute(APPLICATION_VERSION.getValue());
            final double length = Double.parseDouble(request.getFormAttribute(LENGTH.getValue()));
            final long locationCount = Long.parseLong(request.getFormAttribute(LOCATION_COUNT.getValue()));
            final String vehicleType = request.getFormAttribute(VEHICLE_TYPE.getValue());
            final String username = user.principal().getString("username");
            GeoLocation startLocation = null;
            GeoLocation endLocation = null;
            if (locationCount > 0) {
                try {
                    final double startLocationLat = Double
                            .parseDouble(request.getFormAttribute(START_LOCATION_LAT.getValue()));
                    final double startLocationLon = Double
                            .parseDouble(request.getFormAttribute(START_LOCATION_LON.getValue()));
                    final long startLocationTs = Long
                            .parseLong(request.getFormAttribute(START_LOCATION_TS.getValue()));
                    final double endLocationLat = Double
                            .parseDouble(request.getFormAttribute(END_LOCATION_LAT.getValue()));
                    final double endLocationLon = Double
                            .parseDouble(request.getFormAttribute(END_LOCATION_LON.getValue()));
                    final long endLocationTs = Long.parseLong(request.getFormAttribute(END_LOCATION_TS.getValue()));
                    startLocation = new GeoLocation(startLocationLat, startLocationLon, startLocationTs);
                    endLocation = new GeoLocation(endLocationLat, endLocationLon, endLocationTs);
                } catch (final NullPointerException e) {
                    LOGGER.error("Data incomplete!");
                    ctx.fail(422);
                }
            }

            final Set<File> uploads = new HashSet<>();
            ctx.fileUploads().forEach(upload -> uploads.add(new File(upload.uploadedFileName())));

            if (parametersAreValid(deviceId, deviceType, measurementId, osVersion, applicationVersion, length,
                    locationCount, startLocation, endLocation, vehicleType, username, uploads)) {
                informAboutNew(new Measurement(deviceId, measurementId, osVersion, deviceType, applicationVersion,
                        length, locationCount, startLocation, endLocation, vehicleType, username, uploads), ctx);

                response.setStatusCode(201);
                LOGGER.debug("Request was successful!");
                response.end();
            } else {
                ctx.fail(422);
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Data was not parsable!");
            ctx.fail(422);
        }

    }

    /**
     * Informs the system about a new measurement that has arrived.
     * 
     * @param measurement The newly arrived measurement.
     * @param context The routing context necessary to get access to the Vert.x
     *            event bus.
     * @see EventBusAddresses#NEW_MEASUREMENT
     */
    private void informAboutNew(final Measurement measurement, final RoutingContext context) {
        final EventBus eventBus = context.vertx().eventBus();
        eventBus.publish(NEW_MEASUREMENT, measurement);
    }

    /**
     * Checks the validity of the values of all multi part fields, provided together with a measurement.
     * 
     * @param deviceId The world wide unique identifier of the device uploading the data.
     * @param deviceType The type of device uploading the data, such as Pixel 3 or iPhone 6 Plus.
     * @param measurementId The device wide unique identifier of the uploaded measurement.
     * @param osVersion The operating system version, such as Android 9.0.0 or iOS 11.2.
     * @param applicationVersion The version of the app that transmitted the measurement.
     * @param length The length of the measurement in meters.
     * @param locationCount The count of geo locations in the transmitted measurement.
     * @param startLocation The geo location at the beginning of the track represented by the transmitted measurement.
     *            This
     *            value is optional and may not be available for measurements without locations. For measurements with
     *            one location
     *            this equals the {@value #endLocation}.
     * @param endLocation The geo location at the end of the track represented by the transmitted measurement. This
     *            value
     *            is optional and may not be available for measurements without locations. For measurements
     *            with one location this equals the {@value startLocation}.
     * @param vehicleType The type of the vehicle that has captured the measurement.
     * @param username The name of the user uploading the measurement.
     * @param uploads The files transmitted with the upload request.
     * @return <code>true</code> if all field are valid; <code>false</code> otherwise.
     */
    private boolean parametersAreValid(final String deviceId, final String deviceType, final String measurementId,
            final String osVersion, final String applicationVersion, final double length, final long locationCount,
            final GeoLocation startLocation, final GeoLocation endLocation, final String vehicleType,
            final String username, final Set<File> uploads) {
        if (deviceId == null) {
            LOGGER.error("Field deviceId was null!");
            return false;
        }

        if (deviceId.getBytes(Charset.forName("UTF-8")).length != 36) {
            LOGGER.error("Field deviceId was not exactly 128 Bit, which is required for UUIDs!");
            return false;
        }

        if (deviceType == null) {
            LOGGER.error("Field deviceType was null!");
            return false;
        }

        if (deviceType.isEmpty() || deviceType.length() > MAX_GENERIC_METADATA_FIELD_LENGTH) {
            LOGGER.error("Field deviceType had an invalid length of {}!", deviceType.length());
            return false;
        }

        if (measurementId == null) {
            LOGGER.error("Field measurementId was null!");
            return false;
        }

        if (measurementId.isEmpty() || measurementId.length() > 20) {
            LOGGER.error("Field measurementId had an invalid length of {}!", measurementId.length());
            return false;
        }

        if (osVersion == null) {
            LOGGER.error("Field osVersion was null!");
            return false;
        }

        if (osVersion.isEmpty() || osVersion.length() > MAX_GENERIC_METADATA_FIELD_LENGTH) {
            LOGGER.error("Field osVersion had an invalid length of {}!", osVersion.length());
            return false;
        }

        if (applicationVersion == null) {
            LOGGER.error("Field applicationVersion was null!");
            return false;
        }

        if (applicationVersion.isEmpty() || applicationVersion.length() > MAX_GENERIC_METADATA_FIELD_LENGTH) {
            LOGGER.error("Field applicationVersion had an invalid length of {}!", applicationVersion.length());
            return false;
        }

        if (length < 0.0) {
            LOGGER.error("Field length had an invalid value {} which is smaller then 0.0!", length);
            return false;
        }

        if (locationCount < 0L) {
            LOGGER.error("Field locationCount had an invalid value {} which is smaller then 0!", locationCount);
            return false;
        }

        if (locationCount == 0L) {
            if (startLocation != null) {
                LOGGER.error("Field locationCount is 0 but a start location is defined. This is invalid!");
                return false;
            }
            if (endLocation != null) {
                LOGGER.error("Field locationCount is 0 but an end location is defined. This is invalid!");
                return false;
            }
        } else {
            if (startLocation == null) {
                LOGGER.error("Field startLocation was not set for a track with one or more locations!");
                return false;
            }

            if (endLocation == null) {
                LOGGER.error("Field endLocation was not set for a track with one or more locations!");
                return false;
            }
        }

        if (vehicleType == null) {
            LOGGER.error("Field vehicleType was null!");
            return false;
        }

        if (vehicleType.isEmpty() || vehicleType.length() > MAX_GENERIC_METADATA_FIELD_LENGTH) {
            LOGGER.error("Field vehicleType had an invalid length of {}!", vehicleType.length());
            return false;
        }

        if (username == null) {
            LOGGER.error("Field username was null!");
            return false;
        }

        if (username.isEmpty() || username.length() > MAX_GENERIC_METADATA_FIELD_LENGTH) {
            LOGGER.error("Field username had an invalid length of {}!", username.length());
            return false;
        }

        if (uploads.size() != 1) {
            LOGGER.error("MultiPart contained {} files to upload. It should be exactly one!", uploads.size());
            return false;
        }

        return true;

    }

}
