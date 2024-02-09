package de.cyface.collector.storage.cloud

import java.util.UUID

/**
 * An abstraction for the creation of a `CloudStorage` implementation.
 * This is most useful to mock the instance for testing purposes, where no actual communication with the cloud is
 * desired.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
fun interface CloudStorageFactory {
    /**
     * Build a new `CloudStorage` instance.
     */
    fun create(uploadIdentifier: UUID): CloudStorage
}
