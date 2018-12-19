/**
 * 
 */
package de.cyface.collector.verticle;

import org.apache.commons.lang3.Validate;

import de.cyface.collector.Parameter;
import de.cyface.collector.Utils;
import de.cyface.collector.handler.UserCreationHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A verticle that starts up an endpoint that may be used to create new user
 * accounts for the Cyface data collector. This is separated from the Upload API
 * as we do not want to expose this unintentionally to the public.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class ManagementApiVerticle extends AbstractVerticle {
	@Override
	public void start(final Future<Void> startFuture) throws Exception {
		Validate.notNull(startFuture);

		final JsonObject mongoUserDatabaseConfiguration = Parameter.MONGO_USER_DB.jsonValue(getVertx(), null);
		if (mongoUserDatabaseConfiguration == null) {
			startFuture.fail("There was no configuration to access a user database. Please provide one by setting "
					+ Parameter.MONGO_USER_DB.key()
					+ " to the correct value. Please refer to the README for further instructions.");
		}
		final int port = Parameter.MANAGEMENT_HTTP_PORT.intValue(getVertx(), 13371);

		final MongoClient client = Utils.createSharedMongoClient(getVertx(), mongoUserDatabaseConfiguration);
		final MongoAuth mongoAuth = Utils.buildMongoAuthProvider(client);
		final Router router = setupRouter(mongoAuth);
		startHttpServer(startFuture, router, port);
	}

	private Router setupRouter(final MongoAuth mongoAuth) {
		Validate.notNull(mongoAuth);

		Router router = Router.router(getVertx());

		router.post("/user").handler(BodyHandler.create()).blockingHandler(new UserCreationHandler(mongoAuth));

		return router;
	}

	private void startHttpServer(final Future<Void> startFuture, final Router router, final int port) {
		Validate.notNull(startFuture);
		Validate.notNull(router);
		Validate.isTrue(port > 0);

		getVertx().createHttpServer().requestHandler(router).listen(port, res -> {
			if (res.succeeded()) {
				startFuture.complete();
			} else {
				startFuture.fail(res.cause());
			}
		});
	}
}
