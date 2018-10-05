/*
 * Copyright 2018 Cyface GmbH
 * 
 * This file is part of the Cyface Data Collector.
 *
 *  The Cyface Data Collector is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  The Cyface Data Collector is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with the Cyface Data Collector.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.handler;

import static de.cyface.collector.EventBusAddresses.NEW_MEASUREMENT;
import static de.cyface.collector.handler.FormAttributes.DEVICE_ID;
import static de.cyface.collector.handler.FormAttributes.DEVICE_TYPE;
import static de.cyface.collector.handler.FormAttributes.MEASUREMENT_ID;
import static de.cyface.collector.handler.FormAttributes.OS_VERSION;

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

public final class MeasurementHandler implements Handler<RoutingContext> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);

	@Override
	public void handle(final RoutingContext context) {
		LOGGER.info("Received new measurement request.");
		HttpServerRequest request = context.request();
		String deviceId = request.getFormAttribute(DEVICE_ID.getValue());
		String deviceType = request.getFormAttribute(DEVICE_TYPE.getValue());
		String measurementId = request.getFormAttribute(MEASUREMENT_ID.getValue());
		String osVersion = request.getFormAttribute(OS_VERSION.getValue());

		Set<File> uploads = new HashSet<>();
		context.fileUploads().forEach(upload -> uploads.add(new File(upload.uploadedFileName())));

		HttpServerResponse response = context.response();

		if (deviceId == null || deviceType == null || measurementId == null || osVersion == null
				|| uploads.size() == 0) {
			response.setStatusCode(400);
		} else {
			informAboutNew(new Measurement(deviceId, measurementId, osVersion, deviceType, uploads), context);
			response.setStatusCode(201);
		}

		response.end();
	}

	private void informAboutNew(final Measurement measurement, final RoutingContext context) {
		EventBus eventBus = context.vertx().eventBus();
		eventBus.publish(NEW_MEASUREMENT, measurement);
	}
	

}
