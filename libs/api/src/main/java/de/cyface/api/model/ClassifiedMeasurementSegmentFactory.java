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

import io.vertx.core.json.JsonObject;

/**
 * A factory for {@link ClassifiedMeasurementSegment}s.
 *
 * @author Armin Schnabel
 * @since 6.7.0
 * @version 1.0.0
 */
public class ClassifiedMeasurementSegmentFactory implements ClassifiedSegmentFactory<ClassifiedMeasurementSegment> {

    @Override
    public ClassifiedMeasurementSegment build(final JsonObject segment) {
        return new ClassifiedMeasurementSegment(segment);
    }
}
