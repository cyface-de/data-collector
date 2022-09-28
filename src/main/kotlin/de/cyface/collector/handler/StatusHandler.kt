/*
 * Copyright 2021-2022 Cyface GmbH
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

import de.cyface.collector.handler.HTTPStatus.ENTITY_UNPARSABLE
import de.cyface.collector.handler.HTTPStatus.OK
import de.cyface.collector.handler.HTTPStatus.RESUME_INCOMPLETE
import de.cyface.collector.handler.SessionFields.UPLOAD_PATH_FIELD
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A handler for receiving HTTP PUT requests with an empty body on the "measurements" end point.
 *
 *
 * This request type is used by clients to ask the upload status and, thus, where to continue the upload.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 * @property storageService Service used to write and interact with received data.
 */
class StatusHandler (private val storageService: DataStorageService) : Handler<RoutingContext> {
    override fun handle(ctx: RoutingContext) {
        LOGGER.info("Received new upload status request.")
        val request = ctx.request()
        val session = ctx.session()

        // Only accepting requests when client knows the file size
        // i.e. `Content-Range` headers with `bytes */SIZE` but not `bytes */*`
        val rangeRequest = request.getHeader("Content-Range")
        if (!rangeRequest.matches(RANGE_VALUE_CHECK)) {
            LOGGER.error("Content-Range request not supported: {}", rangeRequest)
            ctx.fail(ENTITY_UNPARSABLE)
            return
        }
        try {
            // Check if measurement already exists in database
            val deviceId = request.getHeader(FormAttributes.DEVICE_ID.value)
            val measurementId = request.getHeader(FormAttributes.MEASUREMENT_ID.value)
            LOGGER.debug("Status of {}:{}", deviceId, measurementId)
            if (deviceId == null || measurementId == null) {
                throw InvalidMetaData("Data incomplete!")
            }
            val isStoredResult = storageService.isStored(deviceId, measurementId.toLong())
            isStoredResult.onSuccess {
                val uploadIdentifier = session.get<UUID>(UPLOAD_PATH_FIELD)

                if(it) {
                    LOGGER.debug("Measurement {}:{} is already stored.", deviceId, measurementId)
                    ctx.response().setStatusCode(OK).end()
                } else if (uploadIdentifier == null) {
                    // If no bytes have been received, return 308 but without a `Range` header to indicate this
                    LOGGER.debug("Response: 308, no Range (no path)")
                    ctx.response().putHeader("Content-Length", "0")
                    ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                } else {
                   storageService.bytesUploaded(uploadIdentifier).onSuccess { byteSize ->
                       // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                       val range = String.format("bytes=0-%d", byteSize - 1)
                       LOGGER.debug(String.format("Response: 308, Range %s", range))
                       ctx.response().putHeader("Range", range)
                       ctx.response().putHeader("Content-Length", "0")
                       ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                   }.onFailure {
                       // As this links to a non-existing file, remove this
                       session.remove<UUID>(UPLOAD_PATH_FIELD)

                       // If no bytes have been received, return 308 but without a `Range` header to
                       // indicate this
                       LOGGER.debug("Response: 308, no Range (path, no file)")
                       ctx.response().putHeader("Content-Length", "0")
                       ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                   }
                }
            }
            isStoredResult.onFailure(ctx::fail)
        } catch (e: InvalidMetaData) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        }
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by
         * adapting the values in `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(StatusHandler::class.java)

        /**
         * A regular expression to check the range HTTP header parameter value.
         */
        private val RANGE_VALUE_CHECK = "bytes \\*/[0-9]+".toRegex()
    }
}