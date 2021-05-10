/*
 * Copyright 2019-2021 Cyface GmbH
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

import io.vertx.core.json.JsonObject;

/**
 * An exception that is thrown if an invalid application configuration was found during startup.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 4.0.0
 */
public final class InvalidConfiguration extends RuntimeException {
    /**
     * Used for serializing objects of this class. Only change this if the classes attribute set has been changed.
     */
    private static final long serialVersionUID = 1956168971436087977L;
    /**
     * The invalid configuration that has been encountered.
     */
    private final JsonObject encounteredConfiguration;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param encounteredConfiguration The invalid configuration that has been encountered
     */
    public InvalidConfiguration(final JsonObject encounteredConfiguration) {
        super();
        this.encounteredConfiguration = encounteredConfiguration;
    }

    @Override
    public String getMessage() {
        return String.format("Unable to start application with configuration:%n%s",
                getEncounteredConfiguration().encodePrettily());
    }

    /**
     * @return The invalid configuration that has been encountered
     */
    private JsonObject getEncounteredConfiguration() {
        return encounteredConfiguration;
    }
}
