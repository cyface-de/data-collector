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
package de.cyface.api;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * The interface for pause and resume strategies to be used which wraps async calls in
 * {@link Authorizer#handle(RoutingContext)}.
 * <p>
 * Use {@link PauseAndResumeBeforeBodyParsing} when the `BodyHandler` is not executed before that handler [DAT-749] or
 * {@link PauseAndResumeAfterBodyParsing} otherwise.
 *
 * @author Armin Schnabel
 * @since 6.6.0
 * @version 1.0.0
 */
public interface PauseAndResumeStrategy {

    /**
     * Pauses the request parsing if necessary while, waiting for an async all.
     *
     * @param request The request to be paused
     */
    void pause(final HttpServerRequest request);

    /**
     * Resumes the request parsing if necessary, after an async call was resolved.
     *
     * @param request The request to be resumed
     */
    void resume(final HttpServerRequest request);
}
