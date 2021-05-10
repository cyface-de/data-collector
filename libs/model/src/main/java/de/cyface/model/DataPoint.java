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