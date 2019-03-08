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
import static de.cyface.collector.handler.FormAttributes.DEVICE_ID;
import static de.cyface.collector.handler.FormAttributes.DEVICE_TYPE;
import static de.cyface.collector.handler.FormAttributes.MEASUREMENT_ID;
import static de.cyface.collector.handler.FormAttributes.OS_VERSION;
import static de.cyface.collector.handler.FormAttributes.APPLICATION_VERSION;
import static de.cyface.collector.handler.FormAttributes.LENGTH;
import static de.cyface.collector.handler.FormAttributes.LOCATION_COUNT;
import static de.cyface.collector.handler.FormAttributes.START_LOCATION;
import static de.cyface.collector.handler.FormAttributes.END_LOCATION;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import de.cyface.collector.EventBusAddresses;
import de.cyface.collector.model.Measurement;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler for receiving HTTP POST requests on the "measurements" endpoint.
 * This endpoint is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 * 
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 2.0.0
 */
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
        LOGGER.debug("FormAttributes: " + request.formAttributes());

        try {
            final String deviceId = request.getFormAttribute(DEVICE_ID.getValue());
            final String deviceType = request.getFormAttribute(DEVICE_TYPE.getValue());
            final String measurementId = request.getFormAttribute(MEASUREMENT_ID.getValue());
            final String osVersion = request.getFormAttribute(OS_VERSION.getValue());
            final String applicationVersion = request.getFormAttribute(APPLICATION_VERSION.getValue());
            final double length = Double.parseDouble(request.getFormAttribute(LENGTH.getValue()));
            final long locationCount = Long.parseLong(request.getFormAttribute(LOCATION_COUNT.getValue()));
            final String startLocation = request.getFormAttribute(START_LOCATION.getValue());
            final String endLocation = request.getFormAttribute(END_LOCATION.getValue());

            final Set<File> uploads = new HashSet<>();
            ctx.fileUploads().forEach(upload -> uploads.add(new File(upload.uploadedFileName())));

            if (deviceId == null || deviceType == null || measurementId == null || osVersion == null
                    || applicationVersion == null || startLocation == null || endLocation == null
                    || uploads.size() == 0) {
                LOGGER.debug("Data was deviceId: " + deviceId + ", deviceType: " + deviceType + ", measurementId: "
                        + measurementId + ", osVersion: " + osVersion + ", applicationVersion: " + applicationVersion
                        + ", startLocation: " + startLocation + ", endLocation: " + endLocation);
                ctx.fail(422);
            } else {
                informAboutNew(new Measurement(deviceId, measurementId, osVersion, deviceType, applicationVersion,
                        length, locationCount, startLocation, endLocation, uploads), ctx);

                response.setStatusCode(201);
                LOGGER.debug("Request was successful!");
                response.end();
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Data was not parseable!");
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

}
