/*
 * Copyright 2024 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.handler.upload

import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.ContentRange
import io.vertx.core.http.HttpServerRequest
import java.util.Locale

/**
 * A regular expression to check the range HTTP header parameter value.
 */
private val RANGE_VALUE_CHECK = "bytes \\*/[0-9]+".toRegex()

/**
 * Checks if the information about the upload size in the header exceeds the {@param measurementLimit}.
 *
 * @param measurementLimit The maximal number of `Byte`s which may be uploaded in the upload request.
 * @param uploadLengthField The name of the header field to check.
 * @return the number of bytes to be uploaded
 * @throws Unparsable If the header is missing the expected field about the upload size.
 * @throws PayloadTooLarge If the requested upload is too large.
 */
@Throws(Unparsable::class, PayloadTooLarge::class)
fun HttpServerRequest.checkBodySize(measurementLimit: Long, uploadLengthField: String): Long {
    val uploadLengthString = headers()[uploadLengthField]
        ?: throw Unparsable(
            String.format(
                Locale.ENGLISH,
                "The header is missing the field %s",
                uploadLengthField
            )
        )

    val uploadLength = try {
        uploadLengthString.toLong()
    } catch (e: NumberFormatException) {
        throw Unparsable(
            String.format(
                Locale.ENGLISH,
                "The header field %s is unparsable: %s",
                uploadLengthField,
                uploadLengthString
            )
        )
    }

    if (uploadLength > measurementLimit) {
        throw PayloadTooLarge(
            String.format(
                Locale.ENGLISH,
                "Upload size in the pre-request (%d) is too large, limit is %d bytes.",
                uploadLength,
                measurementLimit
            )
        )
    }

    return uploadLength
}

/**
 * Checks the `Content-Range` field in the request.
 *
 * Only requests are accepted where the client knows the file size, i.e. `Content-Range` headers with
 * `bytes *\/SIZE` but not `bytes *\/\*` (ignore the escape-characters `\` in the documentation).
 */
fun HttpServerRequest.hasValidContentRange(): Boolean {
    val rangeRequest = headers().get("Content-Range")
    return rangeRequest.matches(RANGE_VALUE_CHECK)
}

/**
 * Creates a [ContentRange] instance from this request if that header is set and the resulting size fits the
 * provided `bodySize`. Otherwise, it throws an `Exception`.
 *
 * @param bodySize The number of bytes expected from this request.
 * @return The extracted content range information
 * @throws Unparsable If the content range does not match the body size information
 */
@Throws(Unparsable::class)
fun HttpServerRequest.contentRange(bodySize: Long): ContentRange {
    // The client informs what data is attached: `bytes fromIndex-toIndex/totalBytes`
    val contentRangeString = headers().get("Content-Range")
    val contentRange = ContentRange.fromHTTPHeader(contentRangeString)

    // Make sure content-length matches the content range (to-from+1)
    val sizeAccordingToContentRange = contentRange.toIndex - contentRange.fromIndex + 1L
    if (bodySize != sizeAccordingToContentRange) {
        throw Unparsable(
            String.format(
                Locale.ENGLISH,
                "Upload size (%d) does not match content rang of header (%s)!",
                bodySize,
                contentRange
            )
        )
    }
    return contentRange
}
