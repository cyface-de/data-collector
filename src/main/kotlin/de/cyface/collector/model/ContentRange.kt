
package de.cyface.collector.model

import de.cyface.collector.handler.exception.Unparsable
import org.apache.commons.lang3.Validate
import java.util.Locale

/**
 * The content range information as transmitted by the request header.
 * It consists of the range of bytes uploaded and the total amount of bytes in this upload.
 *
 * The range of bytes (`fromIndex`...`toIndex`) does not necessarily cover all the bytes from `totalBytes`.
 * If an upload is done in chunks, the range might actually be some fraction from somewhere in the middle of the data.
 *
 * For example:
 * ```kotlin
 * ContentRange(2L, 5L, 20L) // Upload Bytes 2, 3, 4 and 5 of 20 total bytes with this request.
 * ContentRange(0L, 4L, 5L) // Upload all five bytes (0, 1, 2, 3 and 4) of the five total bytes of this request.
 * ```
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 * @property fromIndex The zero based byte index to start the content range at (including).
 * @property toIndex The zero based byte index to end the content range at (including).
 * @property totalBytes The total amount of bytes of the document to upload.
 */
data class ContentRange(val fromIndex: Long, val toIndex: Long, val totalBytes: Long) {
    companion object {
        /**
         * The index the actual content range starts in the value of the content-range HTTP header field.
         */
        private const val VALUE_START_INDEX = 6
        fun fromHTTPHeader(contentRangeHeaderValue: String): ContentRange {
            if (!contentRangeHeaderValue.matches(Regex("bytes [0-9]+-[0-9]+/[0-9]+"))) {
                throw Unparsable(
                    String.format(
                        Locale.ENGLISH,
                        "Content-Range request not supported: %s",
                        contentRangeHeaderValue
                    )
                )
            }
            val startingWithFrom = contentRangeHeaderValue.substring(VALUE_START_INDEX)
            val dashPosition = startingWithFrom.indexOf('-')
            Validate.isTrue(dashPosition != -1)
            val from = startingWithFrom.substring(0, dashPosition)
            val startingWithTo = startingWithFrom.substring(dashPosition + 1)
            val slashPosition = startingWithTo.indexOf('/')
            Validate.isTrue(slashPosition != -1)
            val to = startingWithTo.substring(0, slashPosition)
            val total = startingWithTo.substring(slashPosition + 1)
            val parsedFrom = from.toLong()
            val parsedTo = to.toLong()
            val parsedTotal = total.toLong()
            return ContentRange(parsedFrom, parsedTo, parsedTotal)
        }
    }
}
