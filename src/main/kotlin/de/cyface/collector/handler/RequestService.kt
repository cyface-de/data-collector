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
package de.cyface.collector.handler

import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.ext.web.Session
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Interface for services which check upload requests and their pre-requests.
 *
 * @author Armin Schnabel
 */
interface RequestService {
    /**
     * Checks if the information about the upload size in the header exceeds the {@param measurementLimit}.
     *
     * @param headers The header to check.
     * @param measurementLimit The maximal number of `Byte`s which may be uploaded in the upload request.
     * @param uploadLengthField The name of the header field to check.
     * @return the number of bytes to be uploaded
     * @throws Unparsable If the header is missing the expected field about the upload size.
     * @throws PayloadTooLarge If the requested upload is too large.
     */
    @Throws(Unparsable::class, PayloadTooLarge::class)
    fun checkBodySize(headers: MultiMap, measurementLimit: Long, uploadLengthField: String): Long {
        val uploadLengthString = headers[uploadLengthField]
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
     * Checks that no pre-existing session was passed in the pre-request.
     *
     * The purpose of the `pre-request` is to generate an upload session for `upload requests`.
     * Thus, we're expecting that the `SessionHandler` automatically created a new session for this pre-request.
     *
     * @param session the session to check
     * @throws IllegalSession if an existing session was passed
     */
    @Throws(IllegalSession::class)
    fun checkSession(session: Session)

    /**
     * Checks if the file to be uploaded is already stored in the database.
     *
     * This can include checking for the existence of the measurement itself or also for the existence of attachments.
     *
     * @param identifier The id of the transmitted file to check if it already exists.
     * @return A future that will be completed with true if the check are successful. Depending on the implementation
     * this might mean that the measurement does or does not yet exist or that the attachment does not yet exist.
     */
    fun checkConflict(identifier: RequestMetaData.MeasurementIdentifier): Future<Boolean>

    /**
     * Checks if the session is considered "valid", i.e. there was a pre-request which was accepted by the server with
     * the same identifiers as in this request.
     *
     * @param session the session to be checked
     * @param metaData the identifier of this request header
     * @throws IllegalSession if the device-/measurement id of this request does not match the one of the pre-request
     * @throws SessionExpired if the session is not found, e.g. because it expired
     */
    @Throws(IllegalSession::class, SessionExpired::class)
    fun <T : RequestMetaData.MeasurementIdentifier> checkSessionValidity(session: Session, metaData: RequestMetaData<T>)

    /**
     * Extracts the content range information from the request header and checks it matches the body size information
     * from the header.
     *
     * @param headers the request header to check
     * @param bodySize the number of bytes to be uploaded
     * @return the extracted content range information
     * @throws Unparsable if the content range does not match the body size information
     */
    @Throws(Unparsable::class)
    fun contentRange(headers: MultiMap, bodySize: Long): ContentRange {
        // The client informs what data is attached: `bytes fromIndex-toIndex/totalBytes`
        val contentRangeString = headers.get("Content-Range")
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

    /**
     * Checks the `Content-Range` field in the request.
     *
     * Only requests are accepted where the client knows the file size, i.e. `Content-Range` headers with
     * `bytes *\/SIZE` but not `bytes *\/\*` (ignore the escape-characters `\` in the documentation).
     *
     * @param headers the request header to check
     */
    fun checkContentRange(headers: MultiMap): Boolean {
        val rangeRequest = headers.get("Content-Range")
        if (!rangeRequest.matches(RANGE_VALUE_CHECK)) {
            LOGGER.error("Content-Range request not supported: {}", rangeRequest)
            return false
        }
        return true
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by
         * adapting the values in `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(RequestService::class.java)

        /**
         * A regular expression to check the range HTTP header parameter value.
         */
        private val RANGE_VALUE_CHECK = "bytes \\*/[0-9]+".toRegex()
    }
}
