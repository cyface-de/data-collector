/*
 * Copyright 2022 Cyface GmbH
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

import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.common.WebEnvironment;
import io.vertx.ext.web.handler.impl.ErrorHandlerImpl;

/**
 * This custom {@code ErrorHandlerImpl} extension prints the stacktrace of exceptions from `ctx.fail(e)` in the console.
 * <p>
 * We cannot use `ErrorHandler.create(vertx, true)` as this prints stacktrace in the http response not the console.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.10.1
 */
public class FailureHandler extends ErrorHandlerImpl {

    /**
     * The logger used for objects of this class. Configure it using <code>src/main/resources/logback.xml</code>.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(FailureHandler.class);

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param vertx The context required to read the {@code errorTemplateName} from the file system.
     */
    public FailureHandler(final Vertx vertx) {
        super(vertx, DEFAULT_ERROR_HANDLER_TEMPLATE, WebEnvironment.development());
    }

    @Override
    public void handle(final RoutingContext ctx) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Headers:");
            for (final var header : ctx.request().headers()) {
                LOGGER.debug(String.format("%s: %s", header.getKey(), header.getValue()));
            }
        }

        // Printing stacktrace to console is necessary to be able to debug exceptions which only occurs in production
        if (ctx.failure() != null) {
            final var request = ctx.request();
            LOGGER.error(
                    String.format("Stacktrace: %s %s %d", request.method(), request.absoluteURI(), ctx.statusCode()),
                    ctx.failure());
        }

        // Respond to client with pretty error page
        super.handle(ctx);
    }
}