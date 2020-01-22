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

import static de.cyface.collector.EventBusAddressUtils.NEW_MEASUREMENT;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;

import de.cyface.collector.EventBusAddressUtils;
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
 * @version 3.1.4
 * @since 2.0.0
 */
@SuppressWarnings("PMD.AvoidCatchingNPE")
public final class MeasurementHandler implements Handler<RoutingContext> {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.info("Received new measurement request.");
        final HttpServerRequest request = ctx.request();
        final HttpServerResponse response = ctx.response();
        LOGGER.debug("FormAttributes: {}", request.formAttributes());
        final User user = ctx.user();

        try {
            final String deviceId = request.getFormAttribute(FormAttributes.DEVICE_ID.getValue());
            final String deviceType = request.getFormAttribute(FormAttributes.DEVICE_TYPE.getValue());
            final String measurementId = request.getFormAttribute(FormAttributes.MEASUREMENT_ID.getValue());
            final String osVersion = request.getFormAttribute(FormAttributes.OS_VERSION.getValue());
            final String applicationVersion = request.getFormAttribute(FormAttributes.APPLICATION_VERSION.getValue());
            final double length = Double.parseDouble(request.getFormAttribute(FormAttributes.LENGTH.getValue()));
            final long locationCount = Long
                    .parseLong(request.getFormAttribute(FormAttributes.LOCATION_COUNT.getValue()));
            final String vehicleType = request.getFormAttribute(FormAttributes.VEHICLE_TYPE.getValue());
            final String username = user.principal().getString("username");
            GeoLocation startLocation = null;
            GeoLocation endLocation = null;
            if (locationCount > 0) {
                final String startLocationLatString = request
                        .getFormAttribute(FormAttributes.START_LOCATION_LAT.getValue());
                final String startLocationLonString = request
                        .getFormAttribute(FormAttributes.START_LOCATION_LON.getValue());
                final String startLocationTsString = request
                        .getFormAttribute(FormAttributes.START_LOCATION_TS.getValue());
                final String endLocationLatString = request
                        .getFormAttribute(FormAttributes.END_LOCATION_LAT.getValue());
                final String endLocationLonString = request
                        .getFormAttribute(FormAttributes.END_LOCATION_LON.getValue());
                final String endLocationTsString = request.getFormAttribute(FormAttributes.END_LOCATION_TS.getValue());

                if (startLocationLatString == null || startLocationLonString == null || startLocationTsString == null
                        || endLocationLatString == null || endLocationLonString == null
                        || endLocationTsString == null) {
                    LOGGER.error("Data incomplete!");
                    ctx.fail(422);
                }

                final double startLocationLat = Double.parseDouble(startLocationLatString);
                final double startLocationLon = Double.parseDouble(startLocationLonString);
                final long startLocationTs = Long.parseLong(startLocationTsString);
                final double endLocationLat = Double.parseDouble(endLocationLatString);
                final double endLocationLon = Double.parseDouble(endLocationLonString);
                final long endLocationTs = Long.parseLong(endLocationTsString);

                startLocation = new GeoLocation(startLocationLat, startLocationLon, startLocationTs);
                endLocation = new GeoLocation(endLocationLat, endLocationLon, endLocationTs);
            }

            final Set<Measurement.FileUpload> uploads = new HashSet<>();
            ctx.fileUploads()
                    .forEach(upload -> uploads.add(new Measurement.FileUpload(new File(upload.uploadedFileName()),
                            FilenameUtils.getExtension(upload.fileName()))));

            informAboutNew(new Measurement(deviceId, measurementId, osVersion, deviceType, applicationVersion,
                    length, locationCount, startLocation, endLocation, vehicleType, username, uploads), ctx);

            response.setStatusCode(201);
            LOGGER.debug("Request was successful!");
            response.end();

        } catch (final  IllegalArgumentException | NullPointerException e) {
            LOGGER.error("Data was not parsable!", e);
            ctx.fail(422);
        }

    }

    /**
     * Informs the system about a new measurement that has arrived.
     *
     * @param measurement The newly arrived measurement.
     * @param context The routing context necessary to get access to the Vert.x
     *            event bus.
     * @see EventBusAddressUtils#NEW_MEASUREMENT
     */
    private void informAboutNew(final Measurement measurement, final RoutingContext context) {
        final EventBus eventBus = context.vertx().eventBus();
        eventBus.publish(NEW_MEASUREMENT, measurement);
    }
}
