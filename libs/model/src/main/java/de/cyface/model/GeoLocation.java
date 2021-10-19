/*
 * Copyright (C) 2019, 2020 Cyface GmbH - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * A model POJO representing a geographical location measured in latitude and longitude. Typically on earth, but this
 * would work on other planets too, as soon as we require road maintenance there.
 * <p>
 * A record of such a location is represented by a {@link GeoLocationRecord}.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 1.0.0
 */
public class GeoLocation implements Serializable {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = 4613514835798881192L;
    /**
     * The logger used by instances of this class. Configure it using <tt>src/main/resources/logback.xml</tt>.
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(GeoLocation.class);
    /**
     * Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north).
     */
    private double latitude;
    /**
     * Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     * (east).
     */
    private double longitude;
    /**
     * Elevation above sea level in meters or null if it could not be calculated.
     */
    private Double elevation;

    /**
     * No argument constructor as required by Apache Flink. Do not use this in your own code.
     */
    public GeoLocation() {
        // Nothing to do here
    }

    /**
     * Creates a new completely initialized {@link GeoLocation} with not altitude information.
     *
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     */
    public GeoLocation(final double latitude, final double longitude) {
        this(latitude, longitude, null);
    }

    /**
     * Creates a new completely initialized {@link GeoLocation} with altitude information.
     *
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     * @param elevation the elevation above sea level in meters or null if it could not be calculated
     */
    public GeoLocation(final double latitude, final double longitude, final Double elevation) {
        setLatitude(latitude);
        setLongitude(longitude);
        this.elevation = elevation;
    }

  /**
   * Calculates the distance from this geo-locations to another one based on their latitude and longitude. This simple
   * formula assumes the earth is a perfect sphere. As the earth is a spheroid instead, the result can be inaccurate,
   * especially for longer distances.
   * <p>
   * Source: https://stackoverflow.com/a/27943/5815054
   *
   * @param location The location to calculate the distance to
   * @return the estimated distance between both locations in kilometers
   */
  public double distanceTo(final GeoLocation location) {
    final int earthRadiusKm = 6371;
    final double latitudeDifferenceRad = degreeToRad(location.getLatitude() - getLatitude());
    final double longitudeDifferenceRad = degreeToRad(location.getLongitude() - getLongitude());
    final double a = Math.sin(latitudeDifferenceRad / 2) * Math.sin(latitudeDifferenceRad / 2) +
      Math.cos(degreeToRad(getLatitude())) * Math.cos(degreeToRad(location.getLatitude())) *
        Math.sin(longitudeDifferenceRad / 2) * Math.sin(longitudeDifferenceRad / 2);
    final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return earthRadiusKm * c;
  }

  /**
   * Converts a degree value to the "rad" unit.
   * <p>
   * Source: https://stackoverflow.com/a/27943/5815054
   *
   * @param degree the value to be converted in the degree unit
   * @return the value in the rad unit
   */
  private double degreeToRad(final double degree) {
    return degree * (Math.PI / 180);
  }

    @Override
    public String toString() {
        return "GeoLocation{" + "latitude=" + latitude + ", longitude=" + longitude + ", elevation=" + elevation + '}';
    }

    /**
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     */
    public final void setLongitude(final double longitude) {
        if (longitude < -180.0 || longitude > 180.0) {
            // We may only warn here, since some smartphone manufacturers seem to have a strange definition of geo
            // coordinates.
            LOGGER.warn("Setting longitude to invalid value: {}", longitude);
        }
        this.longitude = longitude;
    }

    /**
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     */
    public final void setLatitude(final double latitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            // We may only warn here, since some smartphone manufacturers seem to have a strange definition of geo
            // coordinates.
            LOGGER.warn("Setting latitude to invalid value: {}", latitude);
        }
        this.latitude = latitude;
    }

    /**
     * @param elevation the elevation above sea level in meters or null if it could not be calculated
     */
    public final void setElevation(final Double elevation) {
        this.elevation = elevation;
    }

    /**
     * @return Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * @return Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *         (east)
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * @return the elevation above sea level in meters or null if it could not be calculated
     */
    public Double getElevation() {
        return elevation;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((elevation == null) ? 0 : elevation.hashCode());
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = prime * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = prime * result + (int)(temp ^ (temp >>> 32));
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
        GeoLocation other = (GeoLocation)obj;
        if (elevation == null) {
            if (other.elevation != null) {
                return false;
            }
        } else if (!elevation.equals(other.elevation)) {
            return false;
        }
        if (Double.doubleToLongBits(latitude) != Double.doubleToLongBits(other.latitude)) {
            return false;
        }
      return Double.doubleToLongBits(longitude) == Double.doubleToLongBits(other.longitude);
    }

}
