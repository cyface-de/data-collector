package de.cyface.api.model;

public class ClassifiedSegmentFactoryProvider {

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

    public enum Mode {
        USER_BASED, MEASUREMENT_BASED
    }
}