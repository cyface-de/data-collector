/*
 * Copyright (C) 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.api;

import org.apache.commons.lang3.Validate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;

/**
 * A wrapper which starts the {@code HttpServer} for {@code ApiVerticle}s.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.0.0
 */
public class HttpServer {

    /**
     * The <code>Logger</code> used for objects of this class. Configure it by changing the settings in
     * <code>src/main/resources/vertx-default-jul-logging.properties</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);

    /**
     * HTTP server configuration parameters required to setup the routes
     */
    private final ServerConfig serverConfig;

    /**
     * @param serverConfig HTTP server configuration parameters required to setup the routes
     */
    public HttpServer(final ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    /**
     * Starts the HTTP server provided by this application. This server runs the Cyface Collector REST-API.
     *
     * @param vertx The Vertx instance to get the parameters from
     * @param router The router for all the endpoints the HTTP server should serve
     * @param startPromise Informs the caller about the successful or failed start of the server
     */
    public void start(final Vertx vertx, final Router router, final Promise<Void> startPromise) {
        Validate.notNull(router);
        Validate.notNull(startPromise);

        vertx.createHttpServer().requestHandler(router).listen(serverConfig.getHttpPort(),
                serverStartup -> completeStartup(serverStartup, startPromise));
    }

    /**
     * Finishes the <code>CollectorApiVerticle</code> startup process and informs all interested parties about whether
     * it has been successful or not.
     *
     * @param serverStartup The result of the server startup as provided by <code>Vertx</code>
     * @param promise A promise to call to inform all waiting parties about success or failure of the startup process
     */
    private void completeStartup(final AsyncResult<io.vertx.core.http.HttpServer> serverStartup,
            final Promise<Void> promise) {
        if (serverStartup.succeeded()) {
            promise.complete();
            LOGGER.info("Successfully started Provider API!");
        } else {
            promise.fail(serverStartup.cause());
            LOGGER.info("Starting Provider API failed!");
        }
    }
}