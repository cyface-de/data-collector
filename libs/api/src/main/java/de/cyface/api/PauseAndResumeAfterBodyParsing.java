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

/**
 * Implementation of {@link PauseAndResumeStrategy} which does not pause {@code #request}s when waiting for an async
 * result.
 * <p>
 * This is not necessary when the request body was already parsed before this handler.
 *
 * @author Armin Schnabel
 * @since 6.6.0
 * @version 1.0.0
 */
public class PauseAndResumeAfterBodyParsing implements PauseAndResumeStrategy {

    @Override
    public void pause(final HttpServerRequest request) {
        // Nothing to do
    }

    @Override
    public void resume(final HttpServerRequest request) {
        // Nothing to do
    }
}
