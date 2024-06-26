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
import de.cyface.collector.handler.HTTPStatus.HTTP_CONFLICT
import de.cyface.collector.handler.HTTPStatus.OK
import de.cyface.collector.handler.HTTPStatus.PRECONDITION_FAILED
import de.cyface.collector.handler.HTTPStatus.SERVER_ERROR
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.DEVICE_ID_FIELD
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.MEASUREMENT_ID_FIELD
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.X_UPLOAD_CONTENT_LENGTH_FIELD
import de.cyface.collector.handler.MeasurementPreRequestHandler.Companion.locationUri
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.net.URL

/**
 * A handler for receiving HTTP POST requests on the "attachments" end point.
 * This end point tells the client if the upload may continue or should be skipped.
 *
 * @author Armin Schnabel
 * @property requestService The service to be used to check the request.
 * @property metaService The service to be used to check metadata.
 * @property uploadLimit The maximal number of `Byte`s which may be uploaded in the upload request.
 * @property httpPath The path of the URL under which the Collector is deployed. To ensemble the "Location" header.
 */
class AttachmentPreRequestHandler(
    private val requestService: AttachmentRequestService,
    private val metaService: AttachmentMetaDataService,
    private val uploadLimit: Long,
    private val httpPath: String,
) : Handler<RoutingContext> {

    override fun handle(ctx: RoutingContext) {
        try {
            LOGGER.info("Received new attachment pre-request.")
            val request = ctx.request()
            val session = ctx.session()

            // Check request
            requestService.checkBodySize(request.headers(), uploadLimit, X_UPLOAD_CONTENT_LENGTH_FIELD)
            val metaDataJson = ctx.body().asJsonObject()
            val metaData = metaService.metaData<RequestMetaData.AttachmentIdentifier>(metaDataJson)
            requestService.checkSession(session)

            // Check conflict
            requestService.checkConflict(metaData.identifier)
                .onSuccess { conflict ->
                    if (conflict) {
                        LOGGER.debug("Response: 409, attachment already exists, no upload needed")
                        ctx.response().setStatusCode(HTTP_CONFLICT).end()
                    } else {
                        // Bind session to this attachment and mark as "pre-request accepted"
                        session.put(DEVICE_ID_FIELD, metaData.identifier.deviceId)
                        session.put(MEASUREMENT_ID_FIELD, metaData.identifier.measurementId)
                        session.put(ATTACHMENT_ID_FIELD, metaData.identifier.attachmentId)

                        val requestUri = URL(request.absoluteURI())
                        val protocol = request.getHeader("X-Forwarded-Proto")
                        val locationUri = locationUri(httpPath, requestUri, protocol, session.id())

                        LOGGER.debug("Response 200, Location: {}", locationUri)
                        ctx.response()
                            .putHeader("Location", locationUri.toURL().toExternalForm())
                            .putHeader("Content-Length", "0")
                            .setStatusCode(OK).end()
                    }
                }.onFailure { ctx.fail(SERVER_ERROR, it) }
        } catch (e: InvalidMetaData) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: Unparsable) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: IllegalSession) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: PayloadTooLarge) {
            ctx.fail(ENTITY_UNPARSABLE, e)
        } catch (e: SkipUpload) {
            // Ask the client to skip the upload, e.g. b/c of geofencing, deprecated Binary, missing locations, ...
            // We can add information to the response body to distinguish different causes.
            ctx.fail(PRECONDITION_FAILED, e)
        }
    }

    companion object {
        /**
         * The `Logger` used for objects of this class. Configure it by changing the settings in
         * `src/main/resources/logback.xml`.
         */
        private val LOGGER = LoggerFactory.getLogger(AttachmentPreRequestHandler::class.java)

        /**
         * The field name for the session entry which contains the attachment id if this is an attachment upload.
         *
         * This field is set in the [MeasurementPreRequestHandler] to ensure sessions are bound to attachments and
         * uploads are only accepted with an accepted pre request.
         */
        const val ATTACHMENT_ID_FIELD = "attachment-id"
    }
}
