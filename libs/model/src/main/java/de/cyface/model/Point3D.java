package de.cyface.model;

public interface Point3D {
    /**
     * @return The x component of the data point
     */
    float getX();

    /**
     * @return The y component of the data point
     */
    float getY();

    /**
     * @return The z component of the data point
     */
    float getZ();

    /**
     * @return The Unix timestamp at which this point was measured in milliseconds since the first of January 1970.
     */
    long getTimestamp();
}
