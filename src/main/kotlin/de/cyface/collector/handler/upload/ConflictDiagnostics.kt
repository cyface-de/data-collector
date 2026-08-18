/*
 * Copyright 2026 Cyface GmbH
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

import de.cyface.collector.model.FormAttributes
import de.cyface.collector.storage.StoredMetaData
import de.cyface.collector.storage.lenientDouble
import de.cyface.collector.storage.lenientInt
import de.cyface.collector.storage.lenientLong
import de.cyface.collector.storage.lenientString
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Reports which uploads are rejected as duplicates, and how they differ from what is already stored.
 *
 * A pre-request answered with a conflict tells the client "this is already here", but leaves no trace of which
 * upload that was. That is enough while clients accept the answer; it is not enough when they do not, because the
 * interesting questions then are whether the same devices keep returning, which application version they run, and
 * whether the upload they offer is actually the one already stored or a different one under the same identifier.
 * This class answers those by writing one structured line per rejected upload.
 *
 * **Volume.** A client that retries without backoff can produce millions of conflicts per day for a few thousand
 * uploads. Logging every one of them would drown the log for no extra insight, so reports are deduplicated: a
 * given upload is reported at most once per [windowMillis]. The suppressed repetitions are not lost, they are
 * counted and reported by the *next* line about that upload, so summing the reported counts reconstructs the true
 * number of rejections — except for those still waiting in a window which has not been closed by a further
 * rejection yet. A client that gives up mid-window therefore leaves its last few rejections uncounted, which is
 * the price of counting without a timer. Where an exact total matters, the pre-request metric provides one; this
 * diagnostic is about which uploads are rejected, not about how many.
 *
 * **Cost when switched off.** Everything here is guarded by the log level of [LOGGER_NAME], so the whole feature
 * costs a single flag check while it is disabled, and the storage lookup for the already stored upload happens
 * only for those requests that are actually going to be reported.
 *
 * **What ends up in the log.** Switching this on writes device identifiers, the client's device type and software
 * versions, and the extent of a measurement — its first and last timestamp, its number of locations and its length
 * — into the ordinary application log, for as long as that log is retained. It deliberately carries neither
 * coordinates nor the identity of the user the upload belongs to. Operators who retain logs longer than they
 * retain measurements, or who ship them somewhere the measurements do not go, should weigh that before enabling
 * it, which is the reason it is a switch rather than a permanent fixture.
 *
 * @author Klemens Muthmann
 * @property windowMillis How long a reported upload stays suppressed before it is reported again.
 * @property trackedUploadLimit The largest number of uploads to remember for deduplication. Reaching it means
 * forgetting some, which costs a few duplicate lines but bounds the memory this diagnostic can occupy.
 * @property now Supplies the current time in milliseconds. Injectable so tests can cross a window boundary
 * without waiting for one.
 */
class ConflictDiagnostics(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val trackedUploadLimit: Int = DEFAULT_TRACKED_UPLOAD_LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * The `Logger` this diagnostic writes to.
     *
     * It is deliberately named by a string rather than by this class: the name is the switch an operator uses to
     * turn the diagnostic on and off in the logging configuration, so it must stay stable even if this class is
     * renamed or moved.
     */
    private val logger = LoggerFactory.getLogger(LOGGER_NAME)

    /**
     * The uploads reported recently, keyed by device and measurement, used to suppress repeated reports.
     */
    private val windows = ConcurrentHashMap<String, Window>()

    /**
     * Report that an upload was rejected as a duplicate.
     *
     * The report is written asynchronously, so this returns immediately and must be called *after* the response
     * has been sent: a client waiting for its answer should never wait for a diagnostic.
     *
     * @param requestMetaData The metadata of the rejected pre-request.
     * @param storedMetaData Loads the metadata of the already stored upload. This is a function rather than a
     * value because it is only called for requests which are actually reported, which is a small fraction of them.
     */
    fun record(requestMetaData: JsonObject, storedMetaData: () -> Future<StoredMetaData?>) {
        if (!logger.isInfoEnabled) return
        try {
            val rejections = claimReport(deduplicationKey(requestMetaData) ?: return) ?: return

            storedMetaData()
                .onSuccess { stored -> logger.info(report(requestMetaData, stored, rejections)) }
                .onFailure { cause -> logger.info(report(requestMetaData, null, rejections, cause)) }
        } catch (cause: Exception) {
            // The caller has already answered its client and turned to us only to have a report written. Anything
            // going wrong from here on is therefore ours to absorb: a storage lookup which throws instead of
            // failing its future would otherwise surface as an unhandled exception in the request handler. A
            // diagnostic that can disturb the path it observes is worse than no diagnostic.
            logger.warn("Failed to report a rejected upload.", cause)
        }
    }

    /**
     * Identify the upload a request is about, for the purpose of suppressing repeated reports about it.
     *
     * An attachment carries the identifier of the measurement it belongs to, so leaving its own identifier out
     * would let a reported measurement silence the reports about its attachments.
     *
     * @return The key, or `null` if the request does not identify an upload at all, in which case there is nothing
     * worth reporting.
     */
    private fun deduplicationKey(requestMetaData: JsonObject): String? {
        val deviceId = requestMetaData.lenientString(FormAttributes.DEVICE_ID.value) ?: return null
        val measurementId = requestMetaData.lenientString(FormAttributes.MEASUREMENT_ID.value) ?: return null
        val attachmentId = requestMetaData.lenientString(FormAttributes.ATTACHMENT_ID.value)
        return listOfNotNull(deviceId, measurementId, attachmentId).joinToString(":")
    }

    /**
     * Decide whether the upload identified by [key] is due for a report.
     *
     * @return The number of rejections this report stands for, counting the current one and everything suppressed
     * since the previous report, or `null` if the upload was reported recently enough to stay silent.
     */
    private fun claimReport(key: String): Long? {
        val timestamp = now()
        var rejections: Long? = null
        windows.compute(key) { _, previous ->
            if (previous == null || timestamp - previous.start >= windowMillis) {
                rejections = (previous?.suppressedRejections() ?: 0L) + 1L
                Window(timestamp)
            } else {
                previous.suppress()
                previous
            }
        }
        if (rejections != null) {
            forgetSurplusUploads(timestamp)
        }
        return rejections
    }

    /**
     * Keep the deduplication state from growing without bound.
     *
     * Uploads whose window has passed are of no further use, so they go first. If dropping them is not enough, the
     * state is abandoned wholesale rather than searched for the best candidates: this is a diagnostic, and paying
     * for an eviction strategy with request latency would be the wrong trade.
     *
     * Being wrong costs two things. Some uploads are reported again sooner than their window would suggest, which
     * is harmless. More importantly, the rejections those windows had counted but not yet reported are gone, so
     * the reported counts understate reality afterwards — the same caveat as an unclosed window, only triggered by
     * memory pressure rather than by a client falling silent.
     */
    private fun forgetSurplusUploads(timestamp: Long) {
        if (windows.size <= trackedUploadLimit) return
        windows.values.removeIf { timestamp - it.start >= windowMillis }
        if (windows.size > trackedUploadLimit) {
            windows.clear()
        }
    }

    /**
     * Render a single report as one line of JSON.
     *
     * The line carries no prefix and no message text, so that a log pipeline can parse it as it stands.
     *
     * @param request The metadata of the rejected pre-request.
     * @param stored The metadata of the already stored upload, or `null` if it could not be read.
     * @param rejections The number of rejections this report stands for.
     * @param failure The reason the stored upload could not be read, if that is why [stored] is `null`.
     */
    private fun report(
        request: JsonObject,
        stored: StoredMetaData?,
        rejections: Long,
        failure: Throwable? = null,
    ): String {
        val line = JsonObject()
            .put("evt", EVENT)
            .put("did", request.lenientString(FormAttributes.DEVICE_ID.value))
            .put("mid", request.lenientLong(FormAttributes.MEASUREMENT_ID.value))
            .put("aid", request.lenientLong(FormAttributes.ATTACHMENT_ID.value))
            .put("app", request.lenientString(FormAttributes.APPLICATION_VERSION.value))
            .put("os", request.lenientString(FormAttributes.OS_VERSION.value))
            .put("dev", request.lenientString(FormAttributes.DEVICE_TYPE.value))
            .put("fmt", request.lenientInt(FormAttributes.FORMAT_VERSION.value))
            .put("req", requestValues(request))
            .put("db", stored?.let(::storedValues))
            .put("diff", differences(request, stored, failure))
            .put("n", rejections)
            .put("win", windowMillis / MILLIS_PER_SECOND)
        if (failure != null) {
            line.put("err", failure.message ?: failure::class.java.simpleName)
        }
        return line.encode()
    }

    /**
     * The values of the rejected request which are worth comparing against the stored upload.
     */
    private fun requestValues(request: JsonObject): JsonObject = JsonObject()
        .put("locs", request.lenientLong(FormAttributes.LOCATION_COUNT.value))
        .put("len", request.lenientDouble(FormAttributes.LENGTH.value))
        .put("t0", request.lenientLong(FormAttributes.START_LOCATION_TS.value))
        .put("t1", request.lenientLong(FormAttributes.END_LOCATION_TS.value))
        .put("mod", request.lenientString(FormAttributes.MODALITY.value))

    /**
     * The corresponding values of the already stored upload, plus what only the stored side can tell.
     */
    private fun storedValues(stored: StoredMetaData): JsonObject = JsonObject()
        .put("locs", stored.locationCount)
        .put("len", stored.length)
        .put("t0", stored.startLocationTimestamp)
        .put("t1", stored.endLocationTimestamp)
        .put("mod", stored.modality)
        .put("app", stored.applicationVersion)
        .put("os", stored.operatingSystemVersion)
        .put("dev", stored.deviceType)
        .put("fmt", stored.formatVersion)
        .put("uploaded", stored.uploadDate)

    /**
     * Name the fields in which the rejected request and the stored upload disagree.
     *
     * An empty result is the expected case and means the client is re-offering exactly what is already stored, so
     * it is merely not accepting the answer. A non-empty result is the interesting one: the client considers this
     * a different upload than the one stored under the same identifier.
     *
     * A value which only one side has counts as a disagreement. That is intentional even though old stored uploads
     * predate some of these fields, because "the stored upload cannot tell us" and "the two agree" are different
     * findings and collapsing them would hide the first.
     *
     * @return The differing field names as they are spelled in the report, separated by commas, or one of
     * [NOTHING_STORED] and [STORAGE_UNREADABLE] if there was nothing to compare against. Comma separated rather
     * than a list because a log pipeline can group by a string but not by an array.
     */
    private fun differences(request: JsonObject, stored: StoredMetaData?, failure: Throwable?): String {
        if (failure != null) return STORAGE_UNREADABLE
        if (stored == null) return NOTHING_STORED

        val differing = mutableListOf<String>()
        fun compare(name: String, requestValue: Any?, storedValue: Any?) {
            if (requestValue != storedValue) differing.add(name)
        }
        compare("locs", request.lenientLong(FormAttributes.LOCATION_COUNT.value), stored.locationCount)
        compare("t0", request.lenientLong(FormAttributes.START_LOCATION_TS.value), stored.startLocationTimestamp)
        compare("t1", request.lenientLong(FormAttributes.END_LOCATION_TS.value), stored.endLocationTimestamp)
        compare("mod", request.lenientString(FormAttributes.MODALITY.value), stored.modality)
        compare("app", request.lenientString(FormAttributes.APPLICATION_VERSION.value), stored.applicationVersion)
        compare("os", request.lenientString(FormAttributes.OS_VERSION.value), stored.operatingSystemVersion)
        compare("dev", request.lenientString(FormAttributes.DEVICE_TYPE.value), stored.deviceType)
        compare("fmt", request.lenientInt(FormAttributes.FORMAT_VERSION.value), stored.formatVersion)
        // The length is the one measured value here, so it is compared with a tolerance rather than exactly.
        if (!sameLength(request.lenientDouble(FormAttributes.LENGTH.value), stored.length)) {
            differing.add("len")
        }
        return differing.joinToString(",")
    }

    /**
     * Whether two track lengths are the same measurement, allowing for the rounding a value picks up on its way
     * through a request and into storage.
     */
    private fun sameLength(requestLength: Double?, storedLength: Double?): Boolean = when {
        requestLength == null || storedLength == null -> requestLength == storedLength
        else -> abs(requestLength - storedLength) <= LENGTH_TOLERANCE_IN_METERS
    }

    /**
     * The period during which one upload is reported at most once.
     *
     * @property start When the period began.
     */
    private class Window(val start: Long) {
        /**
         * How many further rejections of this upload were suppressed since it was reported.
         */
        private var suppressed: Long = 0L

        /**
         * Record one rejection that is not going to be reported on its own.
         */
        fun suppress() {
            suppressed++
        }

        /**
         * How many rejections this window swallowed, to be reported by the next line for this upload.
         */
        fun suppressedRejections(): Long = suppressed
    }

    companion object {
        /**
         * The name of the `Logger` carrying these reports.
         *
         * This is a piece of operational interface: setting this logger to `INFO` switches the diagnostic on,
         * setting it to `OFF` switches it off, and both take effect wherever the logging configuration is reloaded
         * without a restart.
         */
        const val LOGGER_NAME = "de.cyface.collector.diagnostics.PreRequestConflict"

        /**
         * Marks these lines among other JSON logs, so a log query can select them without guessing.
         */
        private const val EVENT = "prerequest_conflict"

        /**
         * Reported instead of differing fields when nothing is stored under the identifier after all. The
         * conflict check and this lookup are separate reads, so the upload can disappear between them.
         */
        private const val NOTHING_STORED = "db-missing"

        /**
         * Reported instead of differing fields when the stored upload could not be read at all.
         */
        private const val STORAGE_UNREADABLE = "db-error"

        /**
         * How long one upload stays suppressed after being reported, chosen so that a client retrying several
         * times per second collapses into a few lines per hour while a slow retry loop is still reported promptly.
         */
        private const val DEFAULT_WINDOW_MILLIS = 15L * 60L * 1000L

        /**
         * How many uploads to remember for deduplication. Sized to hold a day of rejected uploads even during a
         * retry storm, while its memory stays a rounding error next to an upload buffer.
         */
        private const val DEFAULT_TRACKED_UPLOAD_LIMIT = 100_000

        /**
         * Two lengths within this many meters of each other are considered the same measurement.
         */
        private const val LENGTH_TOLERANCE_IN_METERS = 0.001

        /**
         * Used to report the window in seconds, which reads better in a log than milliseconds.
         */
        private const val MILLIS_PER_SECOND = 1000L
    }
}
