/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.collector.handler.exception;

/**
 * Exception thrown when the upload session is not found, e.g. when it's expired.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 7.0.0
 */
public class SessionExpired extends Exception {

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param message Details about the reason for this exception.
     */
    public SessionExpired(String message) {
        super(message);
    }

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param message Details about the reason for this exception.
     * @param e The causing error.
     */
    @SuppressWarnings("unused")
    public SessionExpired(String message, RuntimeException e) {
        super(message, e);
    }
}