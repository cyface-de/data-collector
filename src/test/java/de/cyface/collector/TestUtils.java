package de.cyface.collector;

/**
 * Class providing static utility functionality used by the whole test suite.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 * @since 2.0.0
 */
final class TestUtils {
    /**
     * The port of the API endpoint providing access to the test Mongo database instance.
     */
    final static int MONGO_PORT = 12345;

    /**
     * Private constructor to avoid instantiation of static utility class.
     */
    private TestUtils() {
        // Nothing to do here.
    }

}
