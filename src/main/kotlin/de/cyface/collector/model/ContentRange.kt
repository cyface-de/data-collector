package de.cyface.collector.model

/**
 * The content range information as transmitted by the request header.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
data class ContentRange(val fromIndex: String, val toIndex: String, val totalBytes: String)