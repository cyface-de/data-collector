/**
 * 
 */
package de.cyface.collector.handler;

import java.util.ArrayList;

import org.apache.commons.lang3.Validate;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.web.RoutingContext;

/**
 * A handler that creates users inside the user Mongo database.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class UserCreationHandler implements Handler<RoutingContext> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UserCreationHandler.class);
	
	private final MongoAuth mongoAuth;
	
	public UserCreationHandler(final MongoAuth mongoAuth) {
		Validate.notNull(mongoAuth);
		
		this.mongoAuth = mongoAuth;
	}

	@Override
	public void handle(final RoutingContext event) {
		final JsonObject body = event.getBodyAsJson();
		final String username = body.getString("username");
		final String password = body.getString("password");

		mongoAuth.insertUser(username, password, new ArrayList<>(), new ArrayList<>(), ir -> {
			if(ir.succeeded()) {
			LOGGER.info("Added new user with id: " + username);
			event.response().setStatusCode(201).end();
			} else {
				LOGGER.error("Unable to create user with id: "+username, ir.cause());
				event.fail(400);
			}
		});
		
	}

}
