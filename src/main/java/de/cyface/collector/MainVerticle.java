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

/**
 * This Verticle is the Cyface collectors main entry point. It orchestrates all
 * other Verticles and configures the endpoints used to provide the REST-API.
 * 
 * @author Klemens Muthmann
 * @since 2.0.0
 * @version 1.0.0
 */
public class MainVerticle extends AbstractVerticle {

	/**
	 * The <code>Logger</code> used for objects of this class. Configure it by
	 * changing the settings in
	 * <code>src/main/resources/vertx-default-jul-logging.properties</codec>.
	 */
	private final static Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

	@Override
	public void start(final Future<Void> startFuture) throws Exception {

		prepareEventBus();

		deployVerticles();

		final Router router = setupRoutes();

		startHttpServer(router, startFuture);
	}

	/**
	 * Prepares the Vertx event bus during startup of the application.
	 */
	private void prepareEventBus() {
		vertx.eventBus().registerDefaultCodec(Measurement.class, Measurement.getCodec());
	}

	/**
	 * Deploys all additional <code>Verticle</code> objects required by the
	 * application.
	 */
	private void deployVerticles() {
		DeploymentOptions options = new DeploymentOptions().setWorker(true);
		vertx.deployVerticle(SerializationVerticle.class, options);
	}

	/**
	 * @return The Vertx router used by this project.
	 */
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

		final Router mainRouter = Router.router(vertx);
		final Router v2ApiRouter = Router.router(vertx);

		v2ApiRouter.route("/login").handler(BodyHandler.create()).handler(ctx -> {
			try {
				JsonObject body = ctx.getBodyAsJson();
				authProvider.authenticate(body, r -> {
					if (r.succeeded()) {
						LOGGER.info("Authentication successful!");
						String generatedToken = jwtAuthProvider.generateToken(body,
								new JWTOptions().setExpiresInSeconds(60));
						LOGGER.info("New JWT Token: " + generatedToken);
						ctx.response().putHeader("Content-Type", "text/plain").setStatusCode(200).end(generatedToken);
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
		mainRouter.route().last().handler(new DefaultHandler());
		mainRouter.mountSubRouter("/api/v2", v2ApiRouter);

		v2ApiRouter.post("/measurements").handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(false))
				.handler(new MeasurementHandler());
		v2ApiRouter.route().last().handler(new DefaultHandler());

		return mainRouter;
	}

	/**
	 * Starts the HTTP server provided by this application. This server runs the
	 * Cyface collector REST-API.
	 * 
	 * @param router      The router for all the endpoints the HTTP server should
	 *                    serve.
	 * @param startFuture Informs the caller about the successful or failed start of
	 *                    the server.
	 */
	private void startHttpServer(final Router router, final Future<Void> startFuture) {
		String certificateFile = this.getClass().getResource("/localhost.jks").getFile();
		HttpServerOptions options = new HttpServerOptions().setSsl(true).setKeyStoreOptions(new JksOptions().setPath(certificateFile).setPassword("secret"));
		
		vertx.createHttpServer(options).requestHandler(router::accept).listen(config().getInteger("http.port", 8080),
				serverStartup -> completeStartup(serverStartup, startFuture));
	}

	/**
	 * Finishes the <code>MainVerticle</code> startup process and informs all
	 * interested parties about whether it has been successful or not.
	 * 
	 * @param serverStartup
	 * @param future
	 */
	private void completeStartup(final AsyncResult<HttpServer> serverStartup, final Future<Void> future) {
		if (serverStartup.succeeded()) {
			future.complete();
		} else {
			future.fail(serverStartup.cause());
		}
	}

}
