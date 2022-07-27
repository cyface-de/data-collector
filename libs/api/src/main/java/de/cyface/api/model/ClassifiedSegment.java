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

import static de.cyface.model.Json.jsonArray;
import static de.cyface.model.Json.jsonKeyValue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.lang3.Validate;

import de.cyface.model.Json;
import de.cyface.model.Modality;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

// TODO: This should probably be deleted or go into some other project.
/**
 * Class which represents result elements from the surface pipeline: classified road segments.
 * <p>
 * The data in this class is generated from {@code LocationInSegment} data.
 *
 * @author Armin Schnabel
 * @since 6.1.0
 * @version 1.1.0
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
    private final String oid;
    /**
     * {@code true} of this segment is orientated in the same direction as the formal direction of the underlying OSM
     * way id or {@code false} if it's orientated in the opposite direction.
     */
    private final boolean forward;
    /**
     * The geometry of this segment in the GeoJSON format, i.e. containing a `type` attribute with `LineString` as value
     * and the `coordinates` attribute with an array containing the nodes of this segment, as loaded from the database.
     */
    private final Geometry geometry;
    /**
     * The length of this segment in meters.
     */
    private final double length;
    /**
     * The offset in meters from the {@link #getVnk()}}, i.e. where the segment start within the way.
     */
    private final double wayOffset;
    /**
     * The OSM identifier of the OSM way which this segment belongs to.
     */
    private final long way;
    /**
     * The OSM identifier of the node where the underlying OSM way of this segment starts.
     */
    private final long vnk;
    /**
     * The OSM identifier of the node where the underlying OSM way of this segment ends.
     */
    private final long nnk;
    /**
     * A subset of the OSM way's tags which this segment belongs to.
     * <p>
     * The value can be a {@code String}, {@code Double} or an {@code Integer}.
     */
    private final Map<String, Object> tags;
    /**
     * The {@link Modality} this segment was recorded with.
     */
    private final Modality modality;
    /**
     * The time of the newest data point used to classify this segment.
     */
    private final OffsetDateTime latestDataPoint;
    /**
     * The id of the user who uploaded the data of this segment.
     */
    private final String userId;
    /**
     * A mean value from probability theory required to update {@code variance} without requiring previous points.
     */
    private final double expectedValue;
    /**
     * The mathematical variance calculated from calibrated vertical accelerations.
     */
    private final double variance;
    /**
     * The surface quality class calculated for this segment.
     */
    private final SurfaceQuality quality;
    /**
     * The number of sample points used to calculate {@code variance}. This is required to update {@code variance}
     * without requiring previous points.
     */
    private final long dataPointCount;
    /**
     * The version of the format of this segment entry.
     */
    private final String version;

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
     */
    @SuppressWarnings("unused") // Part of the API
    public ClassifiedSegment(final String oid, final boolean forward, final Geometry geometry, final double length,
            final Modality modality, final long vnk, final long nnk, final double wayOffset, final long way,
            final Map<String, Object> tags, final OffsetDateTime latestDataPoint, final String userId,
            final double expectedValue, final double variance, final SurfaceQuality quality, final long dataPointCount,
            final String version) {
        this.oid = oid;
        this.forward = forward;
        this.geometry = geometry;
        this.length = length;
        this.modality = modality;
        this.vnk = vnk;
        this.nnk = nnk;
        this.wayOffset = wayOffset;
        this.tags = tags;
        this.way = way;
        this.latestDataPoint = latestDataPoint;
        this.userId = userId;
        this.expectedValue = expectedValue;
        this.variance = variance;
        this.quality = quality;
        this.dataPointCount = dataPointCount;
        this.version = version;
        Validate.isTrue(version.equals(CURRENT_VERSION));
    }

    /**
     * Constructs a fully initialized {@link ClassifiedSegment} from a database entry.
     *
     * @param segment The entry from the database.
     */
    public ClassifiedSegment(final JsonObject segment) {
        this.oid = segment.getJsonObject("_id").getString("$oid");
        this.forward = segment.getBoolean("forward");
        this.geometry = toGeometry(segment.getJsonObject("geometry"));
        this.length = segment.getDouble("length");
        this.modality = Modality.valueOf(segment.getString("modality"));
        this.way = segment.getLong("way");
        this.vnk = segment.getLong("vnk");
        this.nnk = segment.getLong("nnk");
        this.wayOffset = segment.getDouble("way_offset");
        this.tags = segment.getJsonObject("tags").getMap();
        this.latestDataPoint = OffsetDateTime.parse(segment.getJsonObject("latest_data_point").getString("$date"));
        this.userId = segment.getJsonObject("userId").getString("$oid");
        this.expectedValue = segment.getDouble("expected_value");
        this.variance = segment.getDouble("variance");
        // We're expecting a large number of segments stored, we store the quality class as Integer instead of String.
        // This should reduce the collection size (at least when uncompressed).
        // Later on we might store this as a normalized value between zero and one.
        this.quality = ClassifiedSegment.SurfaceQuality.valueOf(segment.getInteger("quality"));
        this.dataPointCount = segment.getLong("data_point_count");
        this.version = segment.getString("version");
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

        final var properties = propertiesAsGeoJson(handler);
        handler.accept(jsonKeyValue("properties", properties).getStringValue());

        handler.accept("}");
    }

    /**
     * Converts the properties of this measurement as a list of {@link Json.KeyValuePair}s.
     *
     * @return the converted properties
     */
    protected List<Json.KeyValuePair> properties() {

        final var oid = jsonKeyValue("oid", getOid()); // for support
        final var modality = jsonKeyValue("modality", getModality().getDatabaseIdentifier());

        final var way = jsonKeyValue("way", getWay());
        final var forward = jsonKeyValue("forward_moving", isForward());
        final var wayOffset = jsonKeyValue("way_offset", getWayOffset());

        final var length = jsonKeyValue("length", getLength());
        final var vnk = jsonKeyValue("vnk", getVnk());
        final var nnk = jsonKeyValue("nnk", getNnk());
        final var tags = jsonKeyValue("tags", tagsAsGeoJson());

        final var latestDataPoint = jsonKeyValue("timestamp", getLatestDataPoint().toEpochSecond());
        final var quality = jsonKeyValue("quality", getQuality().databaseValue);
        final var color = jsonKeyValue("color",
                getQuality().databaseValue == 0 ? "red"
                        : getQuality().databaseValue == 1 ? "yellow"
                                : getQuality().databaseValue == 2 ? "green"
                                        : getQuality().databaseValue == 3 ? "blue" : "black");

        final var version = jsonKeyValue("version", getVersion());

        return List.of(oid, modality, way, forward, wayOffset, length, vnk, nnk, tags, latestDataPoint,
                quality, color, version);
    }

    /**
     * Exports the properties of this measurement as in the GeoJSON format.
     *
     * @param handler A handler that gets the GeoJson string
     */
    private Json.JsonObject propertiesAsGeoJson(final Consumer<String> handler) {
        final var builder = new Json.JsonObject.Builder();
        properties().forEach(builder::add);
        return builder.build();
    }

    /**
     * Converts the tags of this measurement as {@link Json.JsonObject}.
     */
    protected Json.JsonObject tagsAsGeoJson() {
        final var builder = new Json.JsonObject.Builder();
        addTag(getTags(), "name", builder);
        addTag(getTags(), "highway", builder);
        addTag(getTags(), "surface", builder);
        return builder.build();
    }

    /**
     * Searches the provided {@code tags} for a tag with the key {@code name} and adds this to the provided
     * {@code builder} if found.
     *
     * @param tags the tags to be searched
     * @param key the key of the tag to add
     * @param builder the builder to add the tag to
     */
    private void addTag(final Map<String, Object> tags, final String key, final Json.JsonObject.Builder builder) {
        if (tags.containsKey(key)) {
            final var value = (String)tags.get(key);
            builder.add(jsonKeyValue(key, value));
        }
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

    /**
     * Converts this {@link ClassifiedSegment} into the JSON format for inserting this segment into the database.
     *
     * @return This segment in the JSON format.
     */
    public JsonObject toJson() {
        final var ret = new JsonObject();
        ret.put("_id", new JsonObject().put("$oid", getOid()));
        ret.put("forward", isForward());
        final var geometry = getGeometry();
        final var coordinates = new JsonArray();
        Arrays.stream(geometry.getCoordinates())
                .forEach(c -> coordinates.add(new JsonArray().add(0, c.getLongitude()).add(1, c.getLatitude())));
        ret.put("geometry", new JsonObject().put("type", geometry.getType()).put("coordinates", coordinates));
        ret.put("length", getLength());
        ret.put("modality", getModality().getDatabaseIdentifier());
        ret.put("way", getWay());
        ret.put("vnk", getVnk());
        ret.put("nnk", getNnk());
        ret.put("way_offset", getWayOffset());
        final var tags = new JsonObject();
        getTags().forEach(tags::put);
        ret.put("tags", tags);
        ret.put("latest_data_point", new JsonObject().put("$date", getLatestDataPoint().toString()));
        ret.put("userId", new JsonObject().put("$oid", getUserId()));
        ret.put("expected_value", getExpectedValue());
        ret.put("variance", getVariance());
        ret.put("quality", getQuality().databaseValue);
        ret.put("data_point_count", getDataPointCount());
        ret.put("version", getVersion());
        return ret;
    }

    /**
     * Constructs a fully initialized {@link Geometry} from a database entry.
     *
     * @param geometry The entry from the database.
     * @return The created {@code Geometry} object
     */
    private Geometry toGeometry(final JsonObject geometry) {
        final var type = geometry.getString("type");
        final var array = geometry.getJsonArray("coordinates");
        final var coordinates = new ArrayList<Geometry.Coordinate>();
        array.forEach(coordinate -> {
            final var c = (JsonArray)coordinate;
            coordinates.add(new Geometry.Coordinate(c.getDouble(1), c.getDouble(0)));
        });
        return new Geometry(type, coordinates.toArray(new Geometry.Coordinate[0]));
    }

    @Override
    public String toString() {
        return "ClassifiedSegment{" +
                "CURRENT_VERSION='" + CURRENT_VERSION + '\'' +
                ", oid='" + oid + '\'' +
                ", forward=" + forward +
                ", geometry=" + geometry +
                ", length=" + length +
                ", wayOffset=" + wayOffset +
                ", way=" + way +
                ", vnk=" + vnk +
                ", nnk=" + vnk +
                ", tags=" + tags +
                ", modality=" + modality +
                ", latestDataPoint=" + latestDataPoint +
                ", userId='" + userId + '\'' +
                ", expectedValue=" + expectedValue +
                ", variance=" + variance +
                ", quality=" + quality +
                ", dataPointCount=" + dataPointCount +
                ", version='" + version + '\'' +
                '}';
    }

    /**
     * @return The offset in meters from the {@link #getVnk()}}, i.e. where the segment start within the way.
     */
    @SuppressWarnings("unused") // Part of the API
    public double getWayOffset() {
        return wayOffset;
    }

    /**
     * @return The OSM identifier of the OSM way which this segment belongs to.
     */
    @SuppressWarnings("unused") // Part of the API
    public long getWay() {
        return way;
    }

    /**
     * @return A subset of the OSM way's tags which this segment belongs to. The value can be a {@code String},
     *         {@code Double} or an {@code Integer}.
     */
    @SuppressWarnings("unused") // Part of the API
    public Map<String, Object> getTags() {
        return tags;
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
     *         underlying OSM way id or {@code false} if it's orientated in the opposite direction.
     */
    @SuppressWarnings("unused") // Part of the API
    public boolean isForward() {
        return forward;
    }

    /**
     * @return The geometry of this segment in the GeoJSON format, i.e. containing a `type` attribute with `LineString`
     *         as value and the `coordinates` attribute with an array containing the nodes of this segment, as loaded
     *         from the database.
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
    @SuppressWarnings("unused") // Part of the API
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
     * @return The time of the newest data point used to classify this segment.
     */
    @SuppressWarnings("unused") // Part of the API
    public OffsetDateTime getLatestDataPoint() {
        return latestDataPoint;
    }

    /**
     * @return The id of the user who uploaded the data of this segment.
     */
    @SuppressWarnings("unused") // Part of the API
    public String getUserId() {
        return userId;
    }

    /**
     * @return A mean value from probability theory required to update {@code variance} without requiring previous
     *         points.
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
     *         {@code variance} without requiring previous points.
     */
    @SuppressWarnings("unused") // Part of the API
    public long getDataPointCount() {
        return dataPointCount;
    }

    /**
     * @return The version of the format of this segment entry.
     */
    @SuppressWarnings("unused") // Part of the API
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClassifiedSegment that = (ClassifiedSegment)o;
        return forward == that.forward && Double.compare(that.length, length) == 0
                && Double.compare(that.wayOffset, wayOffset) == 0 && way == that.way
                && vnk == that.vnk && nnk == that.nnk
                && Double.compare(that.expectedValue, expectedValue) == 0
                && Double.compare(that.variance, variance) == 0 && dataPointCount == that.dataPointCount
                && oid.equals(that.oid) && geometry.equals(that.geometry) && tags.equals(that.tags)
                && modality == that.modality && latestDataPoint.equals(that.latestDataPoint)
                && userId.equals(that.userId) && quality == that.quality && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forward, wayOffset, way, modality, userId, version);
    }

    /**
     * The supported surface quality classes for {@link ClassifiedSegment}s.
     *
     * @author Armin Schnabel
     * @since 6.1.0
     * @version 1.0.0
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
