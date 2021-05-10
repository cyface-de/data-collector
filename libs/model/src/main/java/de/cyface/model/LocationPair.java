/*
 * Copyright (C) Cyface GmbH - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.model;

import org.apache.commons.lang3.Validate;

/**
 * A pair of two {@link GeoLocationRecord} objects from the same measurement, captured right after each other.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
public final class LocationPair {
    /**
     * The {@link GeoLocationRecord} that has been captured first.
     */
    private RawRecord earlierLocation;
    /**
     * The {@link GeoLocationRecord} that has been captured second.
     */
    private RawRecord laterLocation;

    /**
     * Required by Flink to instantiate objects of this class throughout the cluster. Never use this in your own code.
     */
    public LocationPair() {
        // Nothing to do here.
    }

    /**
     * Creates a new completely initialized pair of {@link GeoLocationRecord} objects.
     * 
     * @param earlierLocation The {@link GeoLocationRecord} that has been captured first.
     * @param laterLocation The {@link GeoLocationRecord} that has been captured second.
     */
    public LocationPair(final RawRecord earlierLocation, final RawRecord laterLocation) {
        Validate.notNull(earlierLocation, "Earlier location was null while creating a location pair.");
        Validate.notNull(laterLocation, "Later location was null while creating a location pair.");
        Validate.isTrue(earlierLocation.getTimestamp() <= laterLocation.getTimestamp(),
                "Trying to create a location pair with earlier timestamp larger then later timestamp %d:%d",
                earlierLocation.getTimestamp(), laterLocation.getTimestamp());
        Validate.isTrue(earlierLocation.getMeasurementIdentifier().equals(laterLocation.getMeasurementIdentifier()), "Trying to create a location pair with locations from different measurements.");

        this.earlierLocation = earlierLocation;
        this.laterLocation = laterLocation;
    }

    /**
     * @return The {@link GeoLocationRecord} that has been captured first.
     */
    public RawRecord getEarlierLocation() {
        return earlierLocation;
    }

    /**
     * This is required by Apache Flink to recreate objects of this class throughout the cluster. Since this should be
     * an immutable POJO, you should not use this in your own code.
     * 
     * @param earlierLocation The {@link GeoLocationRecord} that has been captured first.
     */
    public void setEarlierLocation(final RawRecord earlierLocation) {
        this.earlierLocation = earlierLocation;
    }

    /**
     * @return The {@link GeoLocationRecord} that has been captured second.
     */
    public RawRecord getLaterLocation() {
        return laterLocation;
    }

    /**
     * This is required by Apache Flink to recreate objects of this class throughout the cluster. Since this should be
     * an immutable POJO, you should not use this in your own code.
     * 
     * @param laterLocation The {@link GeoLocationRecord} that has been captured second.
     */
    public void setLaterLocation(final RawRecord laterLocation) {
        this.laterLocation = laterLocation;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((earlierLocation == null) ? 0 : earlierLocation.hashCode());
        result = prime * result + ((laterLocation == null) ? 0 : laterLocation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LocationPair other = (LocationPair)obj;
        if (earlierLocation == null) {
            if (other.earlierLocation != null) {
                return false;
            }
        } else if (!earlierLocation.equals(other.earlierLocation)) {
            return false;
        }
        if (laterLocation == null) {
            if (other.laterLocation != null) {
                return false;
            }
        } else if (!laterLocation.equals(other.laterLocation)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "LocationPair [earlierLocation=" + earlierLocation + ", laterLocation=" + laterLocation + "]";
    }

}
