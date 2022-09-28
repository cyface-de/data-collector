
package de.cyface.collector.model

/**
 * The content range information as transmitted by the request header.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 * @property fromIndex The zero based byte index to start the content range at (including).
 * @property toIndex The zero based byte index to end the content range at (excluding).
 * @property totalBytes The total amount of bytes. On a properly formatted content range, this should be
 * `toIndex` - `fromIndex`.
 */
data class ContentRange(val fromIndex: String, val toIndex: String, val totalBytes: String)