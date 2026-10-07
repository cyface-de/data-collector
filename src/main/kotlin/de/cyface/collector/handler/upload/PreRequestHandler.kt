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
import de.cyface.collector.handler.HTTPStatus.PRECONDITION_FAILED
import de.cyface.collector.handler.HTTPStatus.SERVER_ERROR
import de.cyface.collector.handler.exception.IllegalSession
import de.cyface.collector.handler.exception.InvalidMetaData
import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.handler.exception.SkipUpload
import de.cyface.collector.handler.exception.Unparsable
import de.cyface.collector.model.FormAttributes
import de.cyface.collector.model.UploadableFactory
import de.cyface.collector.storage.DataStorageService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.micrometer.backends.BackendRegistries
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL

/**
 * A handler for receiving HTTP POST requests.
 * This end point tells the client if the upload may continue or should be skipped.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 * @property uploadableFactory Used to create the correct type of uploadable.
 * @property storage The storage containing already uploaded data.
 * @property uploadLimit The maximal number of `Byte`s which may be uploaded in the upload request.
 * @property httpPath The path of the URL under which the Collector is deployed. To ensemble the "Location" header.
 * @property conflictDiagnostics Reports the uploads rejected as duplicates, while it is switched on.
 */
class PreRequestHandler(
    private val uploadableFactory: UploadableFactory,
    private val storage: DataStorageService,
    private val uploadLimit: Long,
    private val httpPath: String,
    private val conflictDiagnostics: ConflictDiagnostics,
) : Handler<RoutingContext> {

    /**
     * The `Logger` used for objects of this class. Configure it by changing the settings in
     * `src/main/resources/logback.xml`.
     */
    private val logger = LoggerFactory.getLogger(PreRequestHandler::class.java)

    override fun handle(ctx: RoutingContext) {
        try {
            logger.info("Received new upload pre-request.")
            val request = ctx.request()
            val session = ctx.session()

            // Check request
            request.checkBodySize(uploadLimit, X_UPLOAD_CONTENT_LENGTH_FIELD)
            val metaDataJson = ctx.body().asJsonObject()
            val uploadable = uploadableFactory.from(metaDataJson)
            uploadable.check(session)

            // Check conflict
            uploadable.checkConflict(storage)
                .onSuccess { conflict ->
                    if (conflict) {
                        logger.debug("Response: 409, attachment already exists, no upload needed")
                        recordPreRequest(RESULT_CONFLICT, metaDataJson)
                        ctx.response().setStatusCode(HTTP_CONFLICT).end()
                        // Only after the client has its answer: a diagnostic must not delay the response.
                        conflictDiagnostics.record(metaDataJson) { uploadable.storedMetaData(storage) }
                    } else {
                        // Bind session to this attachment and mark as "pre-request accepted"
                        uploadable.bindTo(session)

                        val requestUri = URL(request.absoluteURI())
                        val protocol = request.getHeader("X-Forwarded-Proto")
                        val locationUri = locationUri(httpPath, requestUri, protocol, session.id())

                        logger.debug("Response 200, Location: {}", locationUri)
                        recordPreRequest(RESULT_ACCEPTED, metaDataJson)
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

    /**
     * Records one pre-request outcome as a Prometheus counter, tagged by the client's app version and the
     * [result] (accepted vs. conflict).
     *
     * This makes the duplicate-upload (HTTP 409) storm observable per app version, which the access log cannot
     * provide: nginx only sees the Dart runtime user-agent, not the app version, and the 409 itself is not logged
     * at all (it is a suppressed DEBUG line). The metric rides on the same embedded `/metrics` endpoint as the
     * Vert.x metrics and is a no-op when metrics are disabled (`metrics.enabled=false`), i.e. when no Vert.x
     * Micrometer registry exists yet.
     *
     * @param result One of [RESULT_ACCEPTED] or [RESULT_CONFLICT].
     * @param metaDataJson The parsed pre-request body, used to read the client's `appVersion`.
     */
    private fun recordPreRequest(result: String, metaDataJson: JsonObject) {
        val registry: MeterRegistry = BackendRegistries.getDefaultNow() ?: return
        Counter.builder(PRE_REQUEST_METRIC)
            .description("Upload pre-requests by client app version and outcome (accepted vs. duplicate conflict).")
            .tag("result", result)
            .tag("app_version", appVersionLabel(metaDataJson))
            .register(registry)
            .increment()
    }

    /**
     * Reduces the client-supplied `appVersion` to a bounded metric label so a malformed or hostile value cannot
     * explode the metric cardinality (cf. the historic Vert.x per-UUID path-label problem). Only the leading
     * `major.minor.patch` is kept (build suffixes are dropped); a missing value becomes `unknown`, anything that
     * does not start with a `major.minor.patch` becomes `other`.
     *
     * @param metaDataJson The parsed pre-request body.
     * @return A bounded label value safe to use as a Prometheus tag.
     */
    private fun appVersionLabel(metaDataJson: JsonObject): String {
        val raw = metaDataJson.getString(FormAttributes.APPLICATION_VERSION.value)
        if (raw.isNullOrBlank()) return "unknown"
        return APP_VERSION_PATTERN.find(raw)?.groupValues?.get(1) ?: "other"
    }

    companion object {
        /**
         * The header field which contains the number of bytes of the "requested" upload.
         */
        const val X_UPLOAD_CONTENT_LENGTH_FIELD = "x-upload-content-length"

        /**
         * Name of the pre-request outcome counter. Micrometer exposes it on `/metrics` as
         * `collector_prerequest_total`, tagged with `result` and `app_version`.
         */
        private const val PRE_REQUEST_METRIC = "collector.prerequest"

        /**
         * `result` tag value for an accepted pre-request (the client may follow up with the actual upload).
         */
        private const val RESULT_ACCEPTED = "accepted"

        /**
         * `result` tag value for a duplicate pre-request answered with HTTP 409 (measurement already stored).
         */
        private const val RESULT_CONFLICT = "conflict"

        /**
         * Matches the leading `major.minor.patch` of an app version. The captured group is used as the metric
         * label, which bounds the `app_version` tag cardinality to the set of real released versions.
         */
        private val APP_VERSION_PATTERN = Regex("""^(\d{1,4}\.\d{1,4}\.\d{1,4})""")

        /**
         * Assembles the `Uri` for the `Location` header required by the client who sent the upload request.
         *
         * This contains the upload session id which is needed to start an upload.
         *
         * Google uses the `Location` format:
         * `https://host/endpoint?uploadType=resumable&upload_id=SESSION_ID`
         * To use the Vert.X session parsing we use:
         * `https://host/endpoint?uploadType=resumable&upload_id=(SESSION_ID)"
         *
         * @param httpPath The path of the URL under which the Collector is deployed. To ensemble the "Location" header.
         * @param requestUri the `Uri` to which the upload request was sent.
         * @param protocol the protocol used in the upload request as defined in the request header `X-Forwarded-Proto`
         * @param sessionId the upload session id to be added to the `Location` header assembled.
         * @return The assembled `Location` `Uri` to be returned to the client
         */
        @Deprecated(
            "This requires knowledge about the applications deployment behind a proxy and thus should " +
                "not be used. Instead make the location header relative and avoid rewriting this URL here."
        )
        fun locationUri(httpPath: String, requestUri: URL, protocol: String?, sessionId: String): URI {
            // Our current setup forwards https requests to http internally. As the `Location` returned is automatically
            // used by the Google Client API library for the upload request we ensure the original protocol is used.
            val scheme = protocol ?: requestUri.toURI().scheme
            // The Google Client API library automatically adds the parameter `uploadType` to the Uri. As our upload API
            // offers an endpoint address in the format `measurement/(SID)` we remove the `uploadType` parameter.
            // We don't need to process the `uploadType` as we're only offering one upload type: resumable.
            var query = requestUri.query?.replace("uploadType=resumable", "")
            query = if (query != null && query.isEmpty()) {
                null
            } else {
                query
            }

            var strippedPath = if (httpPath.startsWith("/")) {
                httpPath.subSequence(1..<httpPath.length)
            } else {
                httpPath
            }
            strippedPath = if (strippedPath.endsWith("/")) {
                strippedPath.subSequence(0..<strippedPath.length - 1)
            } else {
                strippedPath
            }
            val path = if (strippedPath.isEmpty()) {
                "${requestUri.path}/($sessionId)/"
            } else {
                "/$strippedPath${requestUri.path}/($sessionId)/"
            }

            return URI(
                scheme,
                null,
                requestUri.host,
                requestUri.port,
                path,
                query,
                null
            )
        }
    }
}
