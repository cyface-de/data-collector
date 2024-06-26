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

import de.cyface.collector.handler.HTTPStatus.ENTITY_UNPARSABLE
import de.cyface.collector.handler.HTTPStatus.NOT_FOUND
import de.cyface.collector.handler.HTTPStatus.PRECONDITION_FAILED
import de.cyface.collector.handler.MeasurementUploadHandler.Companion.checkAndStore
import de.cyface.collector.handler.MeasurementUploadHandler.Companion.onCheckSuccessful
import de.cyface.collector.handler.SessionFields.UPLOAD_PATH_FIELD
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.model.User
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * A handler for receiving HTTP PUT requests on the "attachments" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @property requestService The service to be used to check the request.
 * @property metaService The service to be used to check metadata.
 * @property storageService A service used to store the received data to some persistent data store.
 * @property payloadLimit The maximum number of `Byte`s which may be uploaded.
 *
 * @author Armin Schnabel
 */
class AttachmentUploadHandler(
    private val requestService: AttachmentRequestService,
    private val metaService: AttachmentMetaDataService,
    private val storageService: DataStorageService,
    private val payloadLimit: Long
) : Handler<RoutingContext> {
    init {
        Validate.isTrue(payloadLimit > 0)
    }

    override fun handle(ctx: RoutingContext) {
        val session = ctx.session()
        try {
            LOGGER.info("Received new measurement upload request.")
            val request = ctx.request()

            // Load authenticated user
            val loggedInUser = ctx.get<User?>("logged-in-user")
            if (loggedInUser == null) {
                ctx.response().setStatusCode(HTTPStatus.UNAUTHORIZED).end()
                return
            }

            // Check request
            val bodySize = requestService.checkBodySize(request.headers(), payloadLimit, "content-length")
            val metaData = metaService.metaData<RequestMetaData.AttachmentIdentifier>(request.headers())
            requestService.checkSessionValidity(session, metaData)

            // Handle upload status request
            if (bodySize == 0L) {
                return ctx.next()
            }

            // Handle first chunk
            val contentRange = requestService.contentRange(request.headers(), bodySize)
            checkAndStore(session, loggedInUser, request, contentRange, metaData, storageService)
                .onSuccess { onCheckSuccessful(it, ctx, session) }
                .onFailure(UploadFailureHandler(ctx))
        } catch (e: InvalidMetaData) {
            LOGGER.error("Response: 422", e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: Unparsable) {
            LOGGER.error("Response: 422", e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: IllegalSession) {
            LOGGER.error("Response: 422", e)
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: SessionExpired) {
            LOGGER.warn("Response: 404", e)
            ctx.response().setStatusCode(NOT_FOUND).end() // client sends a new pre-request for this upload
        } catch (e: SkipUpload) {
            LOGGER.debug(e.message, e)
            session.destroy() // client won't resume
            ctx.fail(PRECONDITION_FAILED, e)
        } catch (e: PayloadTooLarge) {
            LOGGER.error("Response: 422", e)
            // client won't resume
            val uploadIdentifier = session.get<UUID?>(UPLOAD_PATH_FIELD)
            if (uploadIdentifier != null) {
                storageService.clean(uploadIdentifier)
            }
            session.destroy()
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: RuntimeException) {
            // Not cleaning session/uploads to allow resume (on uncaught errors)
            ctx.fail(e)
        }
    }

    companion object {
        /**
         * The logger for objects of this class. You can change its configuration by
         * adapting the values in `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(AttachmentUploadHandler::class.java)
    }
}
