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
package de.cyface.api.model;

/**
 * A provider which returns a factory based on a {@link Mode}.
 *
 * @author Armin Schnabel
 * @since 6.7.0
 * @version 1.0.0
 */
public class ClassifiedSegmentFactoryProvider {

    /**
     * Creates a new factory based on a {@link Mode}.
     *
     * @param mode The mode which defines which factory to build.
     * @return The factory build.
     */
    public static ClassifiedSegmentFactory<?> getFactory(final Mode mode) {

        switch (mode) {
            case USER_BASED:
                return new ClassifiedUserSegmentFactory();
            case MEASUREMENT_BASED:
                return new ClassifiedMeasurementSegmentFactory();
            default:
                throw new IllegalArgumentException(String.format("Unknown mode: %s", mode));
        }
    }

    /**
     * Aggregation modes supported for {@link ClassifiedSegment} based classes.
     *
     * @author Armin Schnabel
     * @since 6.7.0
     * @version 1.0.0
     */
    public enum Mode {
        /**
         * This is the default aggregation mode where {@link ClassifiedSegment}s are created for each user, i.e. all
         * data of that user is aggregated to one segment per road section.
         */
        USER_BASED,
        /**
         * Aggregation mode where {@link ClassifiedMeasurementSegment}s are created for each measurement, i.e. all data
         * of that measurement is aggregated to one segment per road section. This mode allows a "history" for segment.
         */
        MEASUREMENT_BASED
    }
}