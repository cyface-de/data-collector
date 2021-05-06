/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.model;

/**
 * The {@link Modality} types to choose from when starting a {@link Measurement}. This class maps the database values to
 * enum values, to make sure the correct values are used within the Java code.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 2.0.0
 * @since 1.0.0
 */
public enum Modality {
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    BICYCLE("BICYCLE"),
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    CAR("CAR"),
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    MOTORBIKE("MOTORBIKE"),
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    BUS("BUS"),
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    TRAIN("TRAIN"),
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    WALKING("WALKING"),
    /**
     * This is used if a bicycle was used to capture a measurement.
     */
    UNKNOWN("UNKNOWN");

    /**
     * The modality types identifier within the database.
     */
    private String databaseIdentifier;

    /**
     * Creates a new enumeration case for a <code>Modality</code>. This constructor is only used internally and thus not
     * visible outside of this package.
     *
     * @param databaseIdentifier The modality types identifier within the database
     */
    Modality(final String databaseIdentifier) {
        this.databaseIdentifier = databaseIdentifier;
    }

    /**
     * @return The modality types identifier within the database
     */
    public String getDatabaseIdentifier() {
        return databaseIdentifier;
    }
}
