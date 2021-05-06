package de.cyface.api;

/**
 * An exception that is thrown if an invalid application configuration was found during startup.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 1.2.0
 */
public final class InvalidConfigurationException extends RuntimeException {
    /**
     * Used for serializing objects of this class. Only change this if the classes attribute set has been changed.
     */
    private static final long serialVersionUID = -7054165263472121737L;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param message with details about the exception
     */
    public InvalidConfigurationException(final String message) {
        super(message);
    }
}
