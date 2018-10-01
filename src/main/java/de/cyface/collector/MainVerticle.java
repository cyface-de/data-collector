package de.cyface.collector;

import java.util.ArrayList;
import java.util.List;

import de.cyface.collector.handler.DefaultHandler;
import de.cyface.collector.handler.MeasurementHandler;
import de.cyface.collector.model.Measurement;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;

public class MainVerticle extends AbstractVerticle {

	private final static Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		prepareEventBus();

		deployVerticles();

		Router router = setupRoutes();

		startHttpServer(router, startFuture);
	}

	private void prepareEventBus() {
		vertx.eventBus().registerDefaultCodec(Measurement.class, Measurement.getCodec());
	}

	private void deployVerticles() {
		DeploymentOptions options = new DeploymentOptions().setWorker(true);
		vertx.deployVerticle(SerializationVerticle.class, options);
	}

	private Router setupRoutes() {
		String keystorePath = this.getClass().getResource("/keystore.jceks").getFile();
		JWTAuthOptions config = new JWTAuthOptions()
				.setKeyStore(new KeyStoreOptions().setPath(keystorePath).setPassword("secret"));

		JWTAuth jwtAuthProvider = JWTAuth.create(vertx, config);
		AuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtAuthProvider, "/login");

		MongoClient client = SerializationVerticle.createSharedMongoClient(vertx, config());
		JsonObject authProperties = new JsonObject();
		MongoAuth authProvider = MongoAuth.create(client, authProperties);
		List<String> roles = new ArrayList<>();
		List<String> permissions = new ArrayList<>();
		client.removeDocuments("user", new JsonObject(), r -> {
			authProvider.insertUser("admin", "secret", roles, permissions,
					ir -> LOGGER.info("Identifier of new user id: " + ir));
		});

		Router mainRouter = Router.router(vertx);
		Router v3ApiRouter = Router.router(vertx);

		mainRouter.route("/login").handler(BodyHandler.create()).handler(ctx -> {
			try {
				JsonObject body = ctx.getBodyAsJson();
				authProvider.authenticate(body, r -> {
					if (r.succeeded()) {
						LOGGER.info("Authentication successful!");
						String generatedToken = jwtAuthProvider.generateToken(body, new JWTOptions().setExpiresInSeconds(60));
						LOGGER.info("New JWT Token: "+generatedToken);
						ctx.response()
							.putHeader("Content-Type", "text/plain")
							.setStatusCode(200)
							.end(generatedToken);
					} else {
						ctx.response().setStatusCode(401).end();
					}
				});
			} catch (DecodeException e) {
				LOGGER.error("Unable to decode body!", e);
				ctx.response().setStatusCode(404).end();
			}
		});
		mainRouter.route().handler(jwtAuthHandler);
		// mainRouter.route().handler(authHandler);
		mainRouter.route().last().handler(new DefaultHandler());
		mainRouter.mountSubRouter("/api/v3", v3ApiRouter);

		v3ApiRouter.post("/measurements").handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(false))
				.handler(new MeasurementHandler());
		v3ApiRouter.route().last().handler(new DefaultHandler());

		return mainRouter;
	}

	private void startHttpServer(final Router router, final Future<Void> startFuture) {
		String certificateFile = this.getClass().getResource("/localhost.jks").getFile();
		HttpServerOptions options = new HttpServerOptions().setSsl(true).setKeyStoreOptions(new JksOptions().setPath(certificateFile).setPassword("secret"));
		
		vertx.createHttpServer(options).requestHandler(router::accept).listen(config().getInteger("http.port", 8080),
				serverStartup -> completeStartup(serverStartup, startFuture));
	}

	private void completeStartup(AsyncResult<HttpServer> serverStartup, Future<Void> future) {
		if (serverStartup.succeeded()) {
			future.complete();
		} else {
			future.fail(serverStartup.cause());
		}
	}

}
