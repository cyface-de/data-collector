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

import de.cyface.collector.handler.HTTPStatus.ENTITY_UNPARSABLE
import de.cyface.collector.handler.HTTPStatus.HTTP_CONFLICT
import de.cyface.collector.handler.HTTPStatus.OK
import de.cyface.collector.handler.HTTPStatus.RESUME_INCOMPLETE
import de.cyface.collector.handler.SessionFields.UPLOAD_PATH_FIELD
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.model.UploadableFactory
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

/**
 * A handler for receiving HTTP PUT requests with an empty body on the "attachments" end point.
 *
 * This request type is used by clients to ask the upload status and, thus, where to continue the upload.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @property uploadableFactory Used to create instances of the object representing this upload
 * @property storage Service used to write and interact with received data.
 */
class StatusHandler(
    private val uploadableFactory: UploadableFactory,
    private val storage: DataStorageService,
) : Handler<RoutingContext> {

    /**
     * The logger for objects of this class. You can change its configuration by
     * adapting the values in `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(StatusHandler::class.java)

    override fun handle(ctx: RoutingContext) {
        val session = ctx.session()
        try {
            logger.info("Received new attachment upload status request.")
            val request = ctx.request()

            // Check request
            if (!request.hasValidContentRange()) {
                logger.error("Content-Range request not supported: {}", request.headers()["Content-Range"])
                ctx.fail(ENTITY_UNPARSABLE)
                return
            }

            // Check upload conflict with existing data
            val uploadable = uploadableFactory.from(request.headers())
            logger.debug("Status of {}", uploadable)
            uploadable.checkConflict(storage)
                .onSuccess { conflict ->
                    val uploadIdentifier = session.get<UUID>(UPLOAD_PATH_FIELD)

                    if (conflict) {
                        logger.debug("Upload {} is already stored.", uploadable)
                        ctx.response().setStatusCode(OK).end()
                    } else if (uploadIdentifier == null) {
                        // When the upload already completed and the UPLOAD_PATH_FIELD was removed from the session
                        // the conflict check above should have resolved to true, so we should not end up here.

                        // Only if no bytes have been received yet (return 308 but w/o `Range` header to indicate this)
                        logger.debug("Response: 308, no Range (no path)")
                        ctx.response().putHeader("Content-Length", "0")
                        ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                    } else {
                        storage.bytesUploaded(uploadIdentifier).onSuccess { byteSize ->
                            // Indicate that, e.g. for 100 received bytes, bytes 0-99 have been received
                            val range = String.format(Locale.GERMAN, "bytes=0-%d", byteSize - 1)
                            logger.debug(String.format(Locale.GERMAN, "Response: 308, Range %s", range))
                            ctx.response().putHeader("Range", range)
                            ctx.response().putHeader("Content-Length", "0")
                            ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                        }.onFailure {
                            // As this links to a non-existing file, remove this
                            session.remove<UUID>(UPLOAD_PATH_FIELD)

                            // If no bytes have been received, return 308 but without a `Range` header to
                            // indicate this
                            logger.debug("Response: 308, no Range (path, no file)")
                            ctx.response().putHeader("Content-Length", "0")
                            ctx.response().setStatusCode(RESUME_INCOMPLETE).end()
                        }
                    }
                }.onFailure(ctx::fail)
        } catch (e: SkipUpload) {
            session.destroy() // client won't resume
            ctx.fail(HTTP_CONFLICT, e)
        } catch (e: InvalidMetaData) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        }
    }
}
