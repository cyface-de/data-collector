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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

/**
 * Default failure handler for all failures not directly handled by any of the other handlers.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 * @since 2.0.0
 */
public final class FailureHandler implements Handler<RoutingContext> {

    /**
     * The logger used for objects of this class. Configure it using <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureHandler.class);

    @Override
    public void handle(final RoutingContext ctx) {
        LOGGER.error(String.format("Invalid resource %s requested!", ctx.request().absoluteURI()));
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Headers:");
            for(Map.Entry<String, String> header:ctx.request().headers()) {
                LOGGER.debug(String.format("%s: %s", header.getKey(), header.getValue()));
            }
        }
        if (ctx.response().ended() || ctx.response().closed()) {
            LOGGER.error("Failure in closed response. Unable to inform client! ", ctx.failure());
        } else {
            ctx.response().setStatusCode(404).end("You seem to have reached an invalid resource.");
        }
    }

}
