package de.cyface.collector;

import static de.cyface.collector.EventBusAddresses.NEW_MEASUREMENT;
import static de.cyface.collector.EventBusAddresses.MEASUREMENT_SAVED;

import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

public class SerializationVerticle extends AbstractVerticle implements Handler<Message<Measurement>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SerializationVerticle.class);
	private MongoClient mongoClient;
	
	@Override
	public void start() throws Exception {
		super.start();
		
		mongoClient = createSharedMongoClient(vertx, config());
		
		registerForNewMeasurements();
	}
	
	private void registerForNewMeasurements() {
		EventBus eventBus = vertx.eventBus();
		eventBus.consumer(NEW_MEASUREMENT, this);
	}

	@Override
	public void handle(Message<Measurement> event) {
		Measurement measurement = event.body();
		JsonObject measurementJson = measurement.toJson();
		
		mongoClient.insert("measurements",measurementJson, res -> {
			if(res.succeeded()) {
				String id = res.result();
				LOGGER.debug("Inserted measurement with id " + id);
				vertx.eventBus().publish(MEASUREMENT_SAVED, id);
			} else {
				res.cause().printStackTrace();
			}
		});
		
	}
	
	public static MongoClient createSharedMongoClient(final Vertx vertx, final JsonObject config) {
		JsonObject mongoConfig = new JsonObject();
		mongoConfig.put("db_name", config.getString("db_name","cyface"));
		mongoConfig.put("connection_string", config.getString("mongodb://127.0.0.1:27017"));
		return MongoClient.createShared(vertx, config);
	}
	
	
}
