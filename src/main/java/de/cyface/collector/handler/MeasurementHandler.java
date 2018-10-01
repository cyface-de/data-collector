package de.cyface.collector.handler;

import static de.cyface.collector.handler.FormAttributes.DEVICE_ID;
import static de.cyface.collector.handler.FormAttributes.DEVICE_TYPE;
import static de.cyface.collector.handler.FormAttributes.MEASUREMENT_ID;
import static de.cyface.collector.handler.FormAttributes.OS_VERSION;
import static de.cyface.collector.EventBusAddresses.NEW_MEASUREMENT;

import java.util.Set;

import de.cyface.collector.model.Measurement;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public final class MeasurementHandler implements Handler<RoutingContext> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementHandler.class);

	@Override
	public void handle(final RoutingContext context) {
		LOGGER.debug("##############Measurements call!");

		HttpServerRequest request = context.request();
		String deviceId = request.getFormAttribute(DEVICE_ID.getValue());
		String deviceType = request.getFormAttribute(DEVICE_TYPE.getValue());
		String measurementId = request.getFormAttribute(MEASUREMENT_ID.getValue());
		String osVersion = request.getFormAttribute(OS_VERSION.getValue());

		Set<FileUpload> uploads = context.fileUploads();

		HttpServerResponse response = context.response();

		if(deviceId==null || deviceType==null || measurementId==null || osVersion==null || uploads.size()==0) {
			response.setStatusCode(400);
		} else {
			informAboutNew(new Measurement(deviceId, measurementId, osVersion, deviceType), context);
			response.setStatusCode(201);
		}

		response.end();
	}

	private void informAboutNew(final Measurement measurement, final RoutingContext context) {
		EventBus eventBus = context.vertx().eventBus();
		eventBus.publish(NEW_MEASUREMENT, measurement);
	}
	

}
