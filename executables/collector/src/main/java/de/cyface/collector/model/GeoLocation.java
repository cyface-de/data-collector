package de.cyface.collector.model;

/**
 * A model of a geo location with latitude and longitude captured at a certain time.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 3.0.0
 */
public final class GeoLocation {
    /**
     * The geographical latitude of this location.
     */
    private final double lat;
    /**
     * The geographical longitude of this location.
     */
    private final double lon;
    /**
     * The time this location was captured at in milliseconds since the first of january 1970.
     */
    private final long timestamp;

    /**
     * Creates a new completely initialized immutable instance of this class.
     * 
     * @param lat The geographical latitude of this location.
     * @param lon The geographical longitude of this location.
     * @param timestamp The time this location was captured at in milliseconds since the first of january 1970.
     */
    public GeoLocation(final double lat, final double lon, final long timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }

    /**
     * @return The geographical latitude of this location.
     */
    public double getLat() {
        return lat;
    }

    /**
     * @return The geographical longitude of this location.
     */
    public double getLon() {
        return lon;
    }

    /**
     * @return The time this location was captured at in milliseconds since the first of january 1970.
     */
    public long getTimestamp() {
        return timestamp;
    }
}
