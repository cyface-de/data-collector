package de.cyface.collector.configuration

import io.vertx.core.Future
import io.vertx.core.Vertx

/**
 * An encryption salt used to complicate brute force attacks on user passwords.
 *
 * Cyface currently uses the same application wide salt value.
 * This is not the most secure way since it is reversible if someone gets their hands on enough passwords.
 * However, at the time of implementation Vertx only supported application wide salts.
 * This has changed with recent Vertx version.
 * A future implementation of Cyface will most likely switch to per user salts.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface Salt {
    /**
     * Provide the salt as an array of bytes.
     *
     * @param vertx The Vertx instance currently used to run this application.
     * @return A future containing the salt as a byte array upon successful completion of this operation.
     */
    fun bytes(vertx: Vertx): Future<ByteArray>
}
