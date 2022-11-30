package de.cyface.collector.storage

/**
 * A description of how to clean up temporary data inside a data storage.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface CleanupOperation {
    /**
     * Cleans all orphaned files, remaining from previous uploads.
     *
     * @param fileExpirationTime The file age in milliseconds after which a temporary file is considered orphaned and
     * thus open to be deleted.
     */
    fun clean(fileExpirationTime: Long)
}
