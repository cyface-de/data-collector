package de.cyface.model;

/**
 * An <code>Exception</code> thrown if a timestamp was not within range of a certain time frame.
 *
 * @author Klemens Muthmann
 */
public class TimestampNotFound extends Exception {

    /**
     * Creates a new exception with an error message.
     * 
     * @param message The error message to report
     */
    public TimestampNotFound(final String message) {
        super(message);
    }
}
