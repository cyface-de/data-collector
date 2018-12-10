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

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles all requests arriving at an unsupported endpoint.
 * 
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 2.0.0
 */
public final class DefaultHandler implements Handler<RoutingContext> {

    /**
     * The logger used for objects of this class. You can change its configuration
     * by changing the appropriate values in
     * <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHandler.class);

    @Override
    public void handle(RoutingContext context) {
        LOGGER.info("Plain API called.");

        // This handler will be called for every request
        HttpServerResponse response = context.response();
        response.putHeader("content-type", "text/plain");

        // Write to the response and end it
        response.end("Cyface Data Collector");
    }

}
