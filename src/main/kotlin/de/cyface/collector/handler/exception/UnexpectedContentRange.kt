package de.cyface.collector.handler.exception

/**
 * Thrown if a request to upload measurement data did not provide a valid content range header.
 *
 * This might happen if the range and the size of the uploaded data does not match for example.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class UnexpectedContentRange(message: String): Exception(message)
