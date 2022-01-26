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
package de.cyface.api.model;

import de.cyface.model.Json;
import de.cyface.model.Modality;
import org.apache.commons.lang3.Validate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static de.cyface.model.Json.jsonArray;
import static de.cyface.model.Json.jsonKeyValue;
import static de.cyface.model.Json.jsonObject;

/**
 * Class which represents result elements from the surface pipeline: classified road segments.
 * <p>
 * The data in this class is generated from {@code LocationInSegment} data.
 *
 * @author Armin Schnabel
 */
@SuppressWarnings("unused") // Part of the API
public class ClassifiedSegment {

    /**
     * The current version of the format of this segment class.
     */
    public final String CURRENT_VERSION = "1.0.0";
    /**
     * The database identifier of the segment.
     */
    final private String oid;
    /**
     * {@code True} of this segment is orientated in the same direction as the formal direction of the underlying OSM
     * way id or {@code false} if it's orientated in the opposite direction.
     */
    final private boolean forward;
    /**
     * The geometry of this segment in the GeoJSON format, i.e. containing a `type` attribute with `LineString` as value
     * and the `coordinates` attribute with an array containing the nodes of this segment, as loaded from the database.
     */
    final private Geometry geometry;
    /**
     * The length of this segment in meters.
     */
    final private double length;
    /**
     * The {@link Modality} this segment was recorded with.
     */
    final private Modality modality;
    /**
     * The OSM id of the node where the underlying OSM way of this segment starts.
     */
    final private long vnk;
    /**
     * The OSM id of the node where the underlying OSM way of this segment ends.
     */
    final private long nnk;
    /**
     * The meter this segment start at compared the full way from `vnk` to `nnk`. I.e. the travel-distance to `vnk`.
     * <p>
     * FIXME: Should we not also store the max_length (100) or segmentation (100)?
     * This helps the client to show "meter 100-200" and to store segments for 25, 100 segmentation at the same time.
     */
    final private int start;
    /**
     * The time of the newest data point used to classify this segment.
     */
    final private OffsetDateTime latestDataPoint;
    /**
     * The name of the user who uploaded the data of this segment.
     */
    final private String username;
    /**
     * A mean value from probability theory required to update {@code variance} without requiring previous points.
     */
    final private double expectedValue;
    /**
     * The mathematical variance calculated from calibrated vertical accelerations.
     */
    final private double variance;
    /**
     * The surface quality class calculated for this segment.
     */
    final private SurfaceQuality quality;
    /**
     * The number of sample points used to calculate {@code variance}. This is required to update {@code variance}
     * without requiring previous points.
     */
    final private long dataPointCount;
    /**
     * The version of the format of this segment entry.
     */
    private final String version;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param oid             The database identifier of the segment.
     * @param forward         {@code True} of this segment is orientated in the same direction as the formal direction of the
     *                        underlying OSM way id or {@code false} if it's orientated in the opposite direction.
     * @param geometry        The geometry of this segment in the GeoJSON format, i.e. containing a `type` attribute with
     *                        `LineString` as value and the `coordinates` attribute with an array containing the nodes of this
     *                        segment, as loaded from the database.
     * @param length          The length of this segment in meters.
     * @param modality        The {@link Modality} this segment was recorded with.
     * @param vnk             The OSM id of the node where the underlying OSM way of this segment starts.
     * @param nnk             The OSM id of the node where the underlying OSM way of this segment ends.
     * @param start           The meter this segment start at compared the full way from `vnk` to `nnk`. I.e. the travel-distance
     *                        to `vnk`.
     * @param latestDataPoint The time of the newest data point used to classify this segment.
     * @param username        The name of the user who uploaded the data of this segment.
     * @param expectedValue   A mean value from probability theory required to update {@code variance} without requiring
     *                        previous points.
     * @param variance        The mathematical variance calculated from calibrated vertical accelerations.
     * @param quality         The surface quality class calculated for this segment.
     * @param dataPointCount  The number of sample points used to calculate {@code variance}. This is required to update
     *                        {@code variance} without requiring previous points.
     * @param version         The version of the format of this segment entry.
     */
    @SuppressWarnings("unused") // Part of the API
    public ClassifiedSegment(final String oid, final boolean forward, final Geometry geometry, final double length,
                             final Modality modality, final long vnk,
                             final long nnk, final int start, final OffsetDateTime latestDataPoint, final String username,
                             final double expectedValue, final double variance,
                             final SurfaceQuality quality, final long dataPointCount, final String version) {
        this.oid = oid;
        this.forward = forward;
        this.geometry = geometry;
        this.length = length;
        this.modality = modality;
        this.vnk = vnk;
        this.nnk = nnk;
        this.start = start;
        this.latestDataPoint = latestDataPoint;
        this.username = username;
        this.expectedValue = expectedValue;
        this.variance = variance;
        this.quality = quality;
        this.dataPointCount = dataPointCount;
        this.version = version;
        Validate.isTrue(version.equals(CURRENT_VERSION));
    }

    /**
     * Exports this measurement as GeoJSON feature.
     *
     * @param handler A handler that gets the GeoJson feature as string
     */
    public void asGeoJson(final Consumer<String> handler) {
        // We decided to generate a String instead of using a JSON library to avoid dependencies in the model library

        // measurement = geoJson "feature"
        handler.accept("{");
        handler.accept(jsonKeyValue("type", "Feature").getStringValue());
        handler.accept(",");

        // All tracks = geometry (MultiLineString)
        handler.accept("\"geometry\":{");
        handler.accept(jsonKeyValue("type", "MultiLineString").getStringValue());
        handler.accept(",");
        handler.accept("\"coordinates\":");
        final var coordinates = convertToLineStringCoordinates(geometry);
        handler.accept(coordinates);
        handler.accept("},");

        final var oid = jsonKeyValue("oid", getOid()); // for support
        final var wayId = jsonKeyValue("@id", "way/232814001"); // FIXME: missing
        final var highwayType = jsonKeyValue("highway", "residential"); // FIXME: missing
        final var surfaceType = jsonKeyValue("surface", "asphalt"); // FIXME: missing
        final var forward = jsonKeyValue("forward_moving", isForward());
        final var modality = jsonKeyValue("modality", getModality().getDatabaseIdentifier());
        final var maxLength = jsonKeyValue("max_length", getLength());
        final var start = jsonKeyValue("start_meter", getStart());
        final var vnk = jsonKeyValue("vnk_id", getVnk());
        final var nnk = jsonKeyValue("nnk_id", getNnk());
        final var latestDataPoint = jsonKeyValue("timestamp", getLatestDataPoint().toEpochSecond());
        final var quality = jsonKeyValue("quality", getQuality().databaseValue);
        final var color = jsonKeyValue("color",
                getQuality().databaseValue == 0 ? "red" :
                        getQuality().databaseValue == 1 ? "yellow" :
                                getQuality().databaseValue == 2 ? "green" :
                                        getQuality().databaseValue == 3 ? "blue" :
                                                "black");
        final var version = jsonKeyValue("version", getVersion());
        final var properties = jsonObject(oid, wayId, highwayType, surfaceType, forward, modality, maxLength, start, vnk, nnk, latestDataPoint, quality, color, version);
        handler.accept(jsonKeyValue("properties", properties).getStringValue());

        handler.accept("}");
    }

    /**
     * Converts a {@link Geometry} to geoJson "coordinates".
     *
     * @param geometry the {@code Geometry}s to be processed
     * @return the string representation of the geoJson coordinates
     */
    private String convertToLineStringCoordinates(final Geometry geometry) {
        final var builder = new StringBuilder("[");

        final var points = jsonArray(
                Arrays.stream(geometry.getCoordinates()).map(c -> geoJsonCoordinates(c).getStringValue())
                        .toArray(String[]::new));
        builder.append(points.getStringValue());
        builder.append(",");
        builder.deleteCharAt(builder.length() - 1); // delete last ","
        builder.append("]");

        return builder.toString();
    }

    private Json.JsonArray geoJsonCoordinates(final Geometry.Coordinate coordinate) {
        return jsonArray(String.valueOf(coordinate.getLongitude()), String.valueOf(coordinate.getLatitude()));
    }

    @Override
    public String toString() {
        return "ClassifiedSegment{" +
                "oid=" + oid +
                ", forward=" + forward +
                ", geometry=" + geometry +
                ", length=" + length +
                ", modality=" + modality +
                ", vnk=" + vnk +
                ", nnk=" + nnk +
                ", start=" + start +
                ", latestDataPoint=" + latestDataPoint +
                ", username='" + username + '\'' +
                ", expectedValue=" + expectedValue +
                ", variance=" + variance +
                ", quality=" + quality +
                ", dataPointCount=" + dataPointCount +
                ", version=" + version +
                '}';
    }

    /**
     * @return The database identifier of the segment.
     */
    @SuppressWarnings("unused") // Part of the API
    public String getOid() {
        return oid;
    }

    /**
     * @return {@code True} of this segment is orientated in the same direction as the formal direction of the
     * underlying OSM way id or {@code false} if it's orientated in the opposite direction.
     */
    @SuppressWarnings("unused") // Part of the API
    public boolean isForward() {
        return forward;
    }

    /**
     * @return The geometry of this segment in the GeoJSON format, i.e. containing a `type` attribute with `LineString`
     * as value and the `coordinates` attribute with an array containing the nodes of this segment, as loaded
     * from the database.
     */
    @SuppressWarnings("unused") // Part of the API
    public Geometry getGeometry() {
        return geometry;
    }

    /**
     * @return The length of this segment in meters.
     */
    @SuppressWarnings("unused") // Part of the API
    public double getLength() {
        return length;
    }

    /**
     * @return The {@link Modality} this segment was recorded with.
     */
    public Modality getModality() {
        return modality;
    }

    /**
     * @return The OSM id of the node where the underlying OSM way of this segment starts.
     */
    @SuppressWarnings("unused") // Part of the API
    public long getVnk() {
        return vnk;
    }

    /**
     * @return The OSM id of the node where the underlying OSM way of this segment ends.
     */
    @SuppressWarnings("unused") // Part of the API
    public long getNnk() {
        return nnk;
    }

    /**
     * @return The meter this segment start at compared the full way from `vnk` to `nnk`. I.e. the travel-distance to
     * `vnk`.
     */
    @SuppressWarnings("unused") // Part of the API
    public int getStart() {
        return start;
    }

    /**
     * @return The time of the newest data point used to classify this segment.
     */
    @SuppressWarnings("unused") // Part of the API
    public OffsetDateTime getLatestDataPoint() {
        return latestDataPoint;
    }

    /**
     * @return The name of the user who uploaded the data of this segment.
     */
    @SuppressWarnings("unused") // Part of the API
    public String getUsername() {
        return username;
    }

    /**
     * @return A mean value from probability theory required to update {@code variance} without requiring previous
     * points.
     */
    @SuppressWarnings("unused") // Part of the API
    public double getExpectedValue() {
        return expectedValue;
    }

    /**
     * @return The mathematical variance calculated from calibrated vertical accelerations.
     */
    @SuppressWarnings("unused") // Part of the API
    public double getVariance() {
        return variance;
    }

    /**
     * @return The surface quality class calculated for this segment.
     */
    @SuppressWarnings("unused") // Part of the API
    public SurfaceQuality getQuality() {
        return quality;
    }

    /**
     * @return The number of sample points used to calculate {@code variance}. This is required to update
     * {@code variance} without requiring previous points.
     */
    @SuppressWarnings("unused") // Part of the API
    public long getDataPointCount() {
        return dataPointCount;
    }

    /**
     * @return The version of the format of this segment entry.
     */
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClassifiedSegment that = (ClassifiedSegment) o;
        return forward == that.forward && Double.compare(that.length, length) == 0 && vnk == that.vnk && nnk == that.nnk
                && start == that.start && Double.compare(that.expectedValue, expectedValue) == 0
                && Double.compare(that.variance, variance) == 0 && dataPointCount == that.dataPointCount
                && Objects.equals(oid, that.oid) && Objects.equals(geometry, that.geometry) && modality == that.modality
                && Objects.equals(latestDataPoint, that.latestDataPoint) && Objects.equals(username, that.username)
                && quality == that.quality && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forward, vnk, nnk, start);
    }

    /**
     * The supported surface quality classes for {@link ClassifiedSegment}s.
     *
     * @author Armin Schnabel
     * @since 6.1.0
     */
    public enum SurfaceQuality {
        WORST(0), BAD(1), GOOD(2), BEST(3);

        private static final Map<Integer, SurfaceQuality> BY_DATABASE_VALUE = new HashMap<>();

        static {
            for (final var e : values()) {
                BY_DATABASE_VALUE.put(e.databaseValue, e);
            }
        }

        /**
         * The value which is stored in the database and represents the associated enum value.
         */
        public final int databaseValue;

        /**
         * Creates a fully initialized instance of this class
         *
         * @param databaseValue The value which is stored in the database and represents the associated enum value.
         */
        SurfaceQuality(final int databaseValue) {
            this.databaseValue = databaseValue;
        }

        /**
         * Creates a fully initialized instance of this class
         *
         * @param databaseValue The value which is stored in the database and represents the associated enum value.
         * @return The created instance
         */
        @SuppressWarnings("unused") // Part of the API
        public static SurfaceQuality valueOf(final int databaseValue) {
            return BY_DATABASE_VALUE.get(databaseValue);
        }
    }
}
