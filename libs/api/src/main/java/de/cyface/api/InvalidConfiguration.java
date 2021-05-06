/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
