/*
 * Copyright 2021-2024 Cyface GmbH
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
import de.cyface.collector.handler.HTTPStatus.RESUME_INCOMPLETE
import de.cyface.collector.handler.SessionFields.UPLOAD_PATH_FIELD
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SessionExpired
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.UnexpectedContentRange
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.model.User
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import de.cyface.collector.storage.StatusType
import de.cyface.collector.storage.UploadMetaData
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID

/**
 * A handler for receiving HTTP PUT requests on the "measurements" end point.
 * This end point is the core of this application and responsible for receiving
 * new measurements from any measurement device and forwarding those
 * measurements for persistent storage.
 *
 * @property checkService The service to be used to check the request.
 * @property metaService The service to be used to check metadata.
 * @property storageService A service used to store the received data to some persistent data store.
 * @property payloadLimit The maximum number of `Byte`s which may be uploaded.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 */
class MeasurementUploadHandler(
    private val checkService: MeasurementRequestService,
    private val metaService: MeasurementMetaDataService,
    private val storageService: DataStorageService,
    private val payloadLimit: Long
) : Handler<RoutingContext> {
    init {
        Validate.isTrue(payloadLimit > 0)
    }

    override fun handle(ctx: RoutingContext) {
        val session = ctx.session()
        try {
            LOGGER.info("Received new attachment upload request.")
            val request = ctx.request()

            // Load authenticated user
            val loggedInUser = ctx.get<User?>("logged-in-user")
            if (loggedInUser == null) {
                ctx.response().setStatusCode(HTTPStatus.UNAUTHORIZED).end()
                return
            }

            // Check request
            val bodySize = checkService.checkBodySize(request.headers(), payloadLimit, "content-length")
            val metaData = metaService.metaData<RequestMetaData.MeasurementIdentifier>(request.headers())
            checkService.checkSessionValidity(session, metaData)

            // Handle upload status request
            if (bodySize == 0L) {
                return ctx.next()
            }

            // Handle first chunk
            val contentRange = checkService.contentRange(request.headers(), bodySize)
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
        private val LOGGER = LoggerFactory.getLogger(MeasurementUploadHandler::class.java)

        /**
         * Called if checking the data and loading it either to temporary storage or to the final location, has finished
         * successfully.
         *
         * @param status: The return status of the check.
         * @param context: The `RoutingContext` used by the current request.
         * @param session: The current HTTP session.
         */
        fun onCheckSuccessful(status: Status, context: RoutingContext, session: Session) {
            when (status.type) {
                StatusType.INCOMPLETE -> {
                    val byteSize = status.byteSize
                    val range = String.format(Locale.ENGLISH, "bytes=0-%d", byteSize - 1)
                    context.response().putHeader("Range", range)
                    context.response().putHeader("Content-Length", "0")
                    context.response().setStatusCode(RESUME_INCOMPLETE).end()
                }

                StatusType.COMPLETE -> {
                    // In case the response does not arrive at the client, the client will receive a 409 on a reupload.
                    // In case of the 409, the client can handle the successful upload. Therefore, the session is
                    // removed here to avoid dangling session references.
                    session.remove<Any>(UPLOAD_PATH_FIELD)
                    context.response().setStatusCode(HTTPStatus.CREATED).end()
                }
            }
        }

        /**
         * Check the request for validity and either fail the response or continue with handling the request.
         *
         * @param session The HTTP session used as a context for this request.
         * @param user The user trying to upload the data.
         * @param sourceData The `ReadStream` containing the data to upload.
         * @param contentRange Range information about the data to upload.
         * This data should have been provided via HTTP content-range parameter.
         * @param metaData Meta information about the measurement to handle.
         * @param storageService A service used to store the received data to some persistent data store.
         */
        fun <T : RequestMetaData.MeasurementIdentifier> checkAndStore(
            session: Session,
            user: User,
            sourceData: ReadStream<Buffer>,
            contentRange: ContentRange,
            metaData: RequestMetaData<T>,
            storageService: DataStorageService,
        ): Future<Status> {
            val ret = Promise.promise<Status>()
            val uploadIdentifier = session.get<UUID?>(UPLOAD_PATH_FIELD)

            if (uploadIdentifier == null && contentRange.fromIndex != 0L) {
                // I.e. the server received data in a previous request but now cannot find the `path` session value.
                // Unsure when this can happen. Not accepting the data. Asking to restart upload (`404`).
                val message = String.format(
                    Locale.ENGLISH,
                    "Response: 404, path is null and unexpected content range: %s",
                    contentRange
                )
                LOGGER.warn(message)
                ret.fail(UnexpectedContentRange(message))
            } else if (uploadIdentifier != null && contentRange.fromIndex == 0L) {
                // Server received data in a previous request but the chunk file was probably cleaned by cleaner task.
                // Can't return 308. Google API client lib throws `Preconditions` on subsequent chunk upload.
                // This makes sense as the server reported before that bytes (>0) were received.
                val message = String.format(
                    Locale.ENGLISH,
                    "Response: 404, Unexpected content range: %s",
                    contentRange
                )
                LOGGER.warn(message)
                ret.fail(UnexpectedContentRange(message))
            } else if (uploadIdentifier == null) {
                acceptNewUpload(session, ret, sourceData, user, contentRange, metaData, storageService)
            } else {
                // Search for previous upload chunk
                storageService.bytesUploaded(uploadIdentifier).onSuccess { byteSize ->
                    // Wrong chunk uploaded
                    if (contentRange.fromIndex != byteSize) {
                        // Ask client to resume from the correct position
                        val range = String.format(Locale.ENGLISH, "bytes=0-%d", byteSize - 1)
                        LOGGER.debug("Response: 308, Range {} (partial data)", range)
                        ret.complete(Status(uploadIdentifier, StatusType.INCOMPLETE, byteSize))
                    } else {
                        val uploadMetaData = UploadMetaData(user, contentRange, uploadIdentifier, metaData)
                        LOGGER.debug("Storing $byteSize bytes to storage service.")
                        val acceptUploadResult = storageService.store(sourceData, uploadMetaData)
                        acceptUploadResult.onSuccess { result -> ret.complete(result) }
                        acceptUploadResult.onFailure { cause -> ret.fail(cause) }
                    }
                }.onFailure {
                    session.remove<Any>(UPLOAD_PATH_FIELD) // was linked to non-existing file
                    acceptNewUpload(session, ret, sourceData, user, contentRange, metaData, storageService)
                }
            }

            return ret.future()
        }

        private fun <T : RequestMetaData.MeasurementIdentifier> acceptNewUpload(
            session: Session,
            uploadAccepted: Promise<Status>,
            sourceData: ReadStream<Buffer>,
            user: User,
            contentRange: ContentRange,
            metaData: RequestMetaData<T>,
            storageService: DataStorageService,
        ) {
            // Create new upload identifier for this upload
            val newUploadIdentifier = UUID.randomUUID()

            // Bind session to this measurement and mark as "pre-request accepted"
            session.put(UPLOAD_PATH_FIELD, newUploadIdentifier)
            val uploadMetaData = UploadMetaData(user, contentRange, newUploadIdentifier, metaData)
            val acceptUpload = storageService.store(sourceData, uploadMetaData)
            acceptUpload.onSuccess { result -> uploadAccepted.complete(result) }
            acceptUpload.onFailure { cause -> uploadAccepted.fail(cause) }
        }
    }
}
