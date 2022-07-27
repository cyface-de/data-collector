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

import static de.cyface.model.Json.jsonKeyValue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.cyface.model.Json;
import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.Modality;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.Validate;

// TODO: This should probably be deleted or go into some other project.
/**
 * Class which represents result elements from the surface pipeline: measurement based classified road segments.
 * <p>
 * The data in this class is generated from {@code LocationInSegment} data.
 *
 * @author Armin Schnabel
 * @since 6.7.0
 * @version 1.0.0
 */
@SuppressWarnings("unused") // Part of the API
public class ClassifiedMeasurementSegment extends ClassifiedSegment {

    /**
     * The identifier of the measurement this {@link ClassifiedMeasurementSegment} is based on.
     */
    private final MeasurementIdentifier measurementIdentifier;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param oid The database identifier of the segment.
     * @param forward {@code true} of this segment is orientated in the same direction as the formal direction of the
     *            underlying OSM way id or {@code false} if it's orientated in the opposite direction.
     * @param geometry The geometry of this segment in the GeoJSON format, i.e. containing a `type` attribute with
     *            `LineString` as value and the `coordinates` attribute with an array containing the nodes of this
     *            segment, as loaded from the database.
     * @param length The length of this segment in meters.
     * @param modality The {@link Modality} this segment was recorded with.
     * @param vnk The OSM id of the node where the underlying OSM way of this segment starts.
     * @param nnk The OSM id of the node where the underlying OSM way of this segment ends.
     * @param wayOffset The offset in meters from the {@link #getVnk()}}, i.e. where the segment start within
     *            the way.
     * @param way The OSM identifier of the OSM way which this segment belongs to.
     * @param tags A subset of the OSM way's tags which this segment belongs to. The value can be a
     *            {@code String}, {@code Double} or an {@code Integer}.
     * @param latestDataPoint The time of the newest data point used to classify this segment.
     * @param userId The id of the user who uploaded the data of this segment.
     * @param expectedValue A mean value from probability theory required to update {@code variance} without requiring
     *            previous points.
     * @param variance The mathematical variance calculated from calibrated vertical accelerations.
     * @param quality The surface quality class calculated for this segment.
     * @param dataPointCount The number of sample points used to calculate {@code variance}. This is required to update
     *            {@code variance} without requiring previous points.
     * @param version The version of the format of this segment entry.
     * @param measurementIdentifier The identifier of the measurement this {@link ClassifiedMeasurementSegment} is based
     *            on.
     */
    @SuppressWarnings("unused") // Part of the API
    public ClassifiedMeasurementSegment(final String oid, final boolean forward, final Geometry geometry,
            final double length,
            final Modality modality, final long vnk, final long nnk, final double wayOffset, final long way,
            final Map<String, Object> tags, final OffsetDateTime latestDataPoint, final String userId,
            final double expectedValue, final double variance, final SurfaceQuality quality, final long dataPointCount,
            final String version, final MeasurementIdentifier measurementIdentifier) {

        super(oid, forward, geometry, length, modality, vnk, nnk, wayOffset, way, tags, latestDataPoint, userId,
                expectedValue, variance, quality, dataPointCount, version);
        this.measurementIdentifier = Validate.notNull(measurementIdentifier);
    }

    /**
     * Constructs a fully initialized {@link ClassifiedSegment} from a database entry.
     *
     * @param segment The entry from the database.
     */
    public ClassifiedMeasurementSegment(final JsonObject segment) {

        super(segment);
        final var deviceId = Validate.notEmpty(segment.getString("deviceId"));
        final var measurementId = Validate.notNull(segment.getLong("measurementId"));
        this.measurementIdentifier = new MeasurementIdentifier(deviceId, measurementId);
    }

    @Override
    protected List<Json.KeyValuePair> properties() {
        final var properties = new ArrayList<>(super.properties());
        properties.add(jsonKeyValue("deviceId", measurementIdentifier.getDeviceIdentifier()));
        properties.add(jsonKeyValue("measurementId", measurementIdentifier.getMeasurementIdentifier()));
        return properties;
    }

    @Override
    public JsonObject toJson() {
        final var ret = super.toJson();
        ret.put("deviceId", measurementIdentifier.getDeviceIdentifier());
        ret.put("measurementId", measurementIdentifier.getMeasurementIdentifier());
        return ret;
    }

    /**
     * @return The identifier of the measurement this {@link ClassifiedMeasurementSegment} is based on.
     */
    public MeasurementIdentifier getMeasurementIdentifier() {
        return measurementIdentifier;
    }

    @Override
    public String toString() {
        return "ClassifiedMeasurementSegment{" +
                "measurementIdentifier=" + measurementIdentifier +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        ClassifiedMeasurementSegment that = (ClassifiedMeasurementSegment)o;
        return measurementIdentifier.equals(that.measurementIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), measurementIdentifier);
    }
}
