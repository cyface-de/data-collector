package de.cyface.collector.verticle

/**
 * An exception thrown if an invalid configuration of this application has been encountered.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class InvalidConfig(override val message: String) : Exception(message)
