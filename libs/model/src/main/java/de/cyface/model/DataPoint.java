/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.model;

/**
 * A single point of data such as a geo location or an acceleration measurement. <code>DataPoint</code> instances are
 * <code>Comparable</code> based on their timestamp from earliest to latest.
 *
 * @author Klemens Muthmann
 */
public interface DataPoint extends Comparable<DataPoint> {
    /**
     * @return The Unix timestamp at which this <code>DataPoint</code> was measured in milliseconds since the first of
     *         January 1970.
     */
    long getTimestamp();

    /**
     * @param timestamp The Unix timestamp at which this <code>DataPoint</code> was measured in milliseconds since the
     *            first of January 1970.
     */
    void setTimestamp(final long timestamp);
}