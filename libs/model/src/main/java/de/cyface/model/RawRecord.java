package de.cyface.model;

import org.apache.commons.lang3.Validate;

public class RawRecord extends GeoLocationRecord {

    /**
     * Used to serialize objects of this class. Only change this value if this classes attribute set changes.
     */
    private static final long serialVersionUID = 518958180513770078L;
    /**
     * The measurement accuracy of this location in meters. This accuracy is usually the result of imperfect
     * measurement hardware.
     */
    private double accuracy;
    /**
     * The traveled speed in meters per second as reported by the geo location system (e.g. GPS, GLONASS, GALILEO) during this recording.
     */
    private double speed;
    /**
     * The modality type used while collecting this location or {@code null} if the information is not available.
     */
    private Modality modality;

    /**
     * No-argument constructor as required by Apache Flink. Do not use this in your own code.
     */
    public RawRecord() {
        // Nothing to do here.
    }

    public RawRecord(final MeasurementIdentifier measurementIdentifier, final long timestamp,
            final double latitude, final double longitude, final double accuracy, final double speed, final Modality modality) {
        super(measurementIdentifier, timestamp, latitude, longitude, null);

        setAccuracy(accuracy);
        setSpeed(speed);
        setModality(modality);
    }

    /**
     * Creates a new completely initialized <code>RawRecord</code>.
     *
     * @param measurementIdentifier The world wide unique identifier of the {@link Measurement} this record belongs to
     * @param timestamp The timestamp this location was captured on in milliseconds since 1st January 1970 (epoch)
     * @param latitude Geographical latitude in coordinates (decimal fraction) raging from -90° (south) to 90° (north)
     * @param longitude Geographical longitude in coordinates (decimal fraction) ranging from -180° (west) to 180°
     *            (east)
     * @param elevation The elevation above sea level in meters or null if it could not be calculated
     * @param accuracy The measurement accuracy of this location in meters. This accuracy is usually the result of
     *            imperfect measurement hardware
     * @param speed The traveled speed as reported by the geo location system (e.g. GPS, GLONASS, GALILEO) during this
     *            recording
     * @param modality The modality type used while collecting the location or {@code null} if the information is not
     *            available
     */
    public RawRecord(final MeasurementIdentifier measurementIdentifier, final long timestamp,
            final double latitude, final double longitude, final Double elevation, final double accuracy,
            final double speed, final Modality modality) {
        super(measurementIdentifier, timestamp, latitude, longitude, elevation);

        setAccuracy(accuracy);
        setSpeed(speed);
        setModality(modality);
    }

    /**
     * @return The measurement accuracy of this location in meters. This accuracy is usually the result of imperfect
     *         measurement hardware.
     */
    public final double getAccuracy() {
        return accuracy;
    }

    /**
     * @param accuracy The measurement accuracy of this location in meters. This accuracy is usually the result of
     *            imperfect measurement hardware.
     */
    public final void setAccuracy(double accuracy) {
        Validate.isTrue(accuracy >= 0.0);
        this.accuracy = accuracy;
    }

    /**
     * @return The traveled speed as reported by the geo location system (e.g. GPS, GLONASS, GALILEO) during this
     *         recording
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * @return The modality type used while collecting this location or {@code null} if the information is not
     *         available.
     */
    public Modality getModality() {
        return modality;
    }

    /**
     * Required by Apache Flink.
     *
     * @param accuracy The measurement accuracy of this location in meters. This accuracy is usually the result of
     *            imperfect measurement hardware.
     */
    public void setAccuracy(int accuracy) {
        this.accuracy = accuracy;
    }

    /**
     * @param speed The traveled speed as reported by the geo location system (e.g. GPS, GLONASS, GALILEO) during this
     *            recording
     */
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /**
     * Required by Apache Flink and to annotate the modality types separately.
     *
     * @param modality The modality type used while collecting this location or {@code null} if the information is not
     *            available.
     */
    public void setModality(Modality modality) {
        this.modality = modality;
    }
}
