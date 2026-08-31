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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import de.cyface.collector.model.FormAttributes
import de.cyface.collector.storage.StoredMetaData
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Tests for [ConflictDiagnostics], the report about uploads rejected as duplicates.
 *
 * The reports are log lines, so these tests capture them with a [ListAppender] attached to the diagnostic's own
 * logger and read the JSON back out of the captured messages.
 *
 * @author Klemens Muthmann
 */
class ConflictDiagnosticsTest {

    /**
     * The logger the class under test writes to, reconfigured for the duration of each test.
     */
    private lateinit var diagnosticsLogger: Logger

    /**
     * Collects the reports written during a test.
     */
    private lateinit var reports: ListAppender<ILoggingEvent>

    /**
     * The level the diagnostics logger had before a test changed it.
     */
    private var previousLevel: Level? = null

    /**
     * The time the object under test sees, so a test can cross a window boundary without waiting for one.
     */
    private var currentTime: Long = 0L

    @BeforeEach
    fun setUp() {
        diagnosticsLogger = LoggerFactory.getLogger(ConflictDiagnostics.LOGGER_NAME) as Logger
        previousLevel = diagnosticsLogger.level
        diagnosticsLogger.level = Level.INFO
        // An appender which was never started drops every event without complaining, which would leave these
        // tests asserting on an empty list and passing for the wrong reason.
        reports = ListAppender<ILoggingEvent>().apply { start() }
        diagnosticsLogger.addAppender(reports)
        currentTime = 1_000_000L
    }

    @AfterEach
    fun tearDown() {
        // The logger is global, so leaving the appender attached would carry it into every following test.
        diagnosticsLogger.detachAppender(reports)
        reports.stop()
        diagnosticsLogger.level = previousLevel
    }

    @Test
    fun `Report identifies the rejected upload and the software which sent it`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData()))

        // Assert
        val report = singleReport()
        assertThat(report.getString("evt"), equalTo("prerequest_conflict"))
        assertThat(report.getString("did"), equalTo(DEVICE_ID))
        assertThat(report.getLong("mid"), equalTo(MEASUREMENT_ID))
        assertThat(report.getString("app"), equalTo("4.0.25"))
        assertThat(report.getString("os"), equalTo("Android 14"))
        assertThat(report.getString("dev"), equalTo("SM-A546B"))
    }

    @Test
    fun `Report shows no differences when the client re-offers what is already stored`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData()))

        // Assert
        assertThat(singleReport().getString("diff"), equalTo(""))
    }

    @Test
    fun `Report names a differing location count`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData(locationCount = 811L)))

        // Assert
        assertThat(singleReport().getString("diff"), equalTo("locs"))
    }

    @Test
    fun `Report names differing start and end times`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData(startTime = 1L, endTime = 2L)))

        // Assert
        assertThat(singleReport().getString("diff"), equalTo("t0,t1"))
    }

    @Test
    fun `Report accepts a track length which only differs by rounding`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData(length = LENGTH + 0.0000001)))

        // Assert
        assertThat(singleReport().getString("diff"), equalTo(""))
    }

    @Test
    fun `Report carries both the requested and the stored values so they can be compared`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData(locationCount = 811L, applicationVersion = "4.0.22")))

        // Assert
        val report = singleReport()
        assertThat(report.getJsonObject("req").getLong("locs"), equalTo(LOCATION_COUNT))
        assertThat(report.getJsonObject("db").getLong("locs"), equalTo(811L))
        assertThat(report.getJsonObject("db").getString("app"), equalTo("4.0.22"))
    }

    @Test
    fun `A repeated rejection of the same upload is not reported again within the window`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        repeat(5) { oocut.record(preRequest(), stored(storedMetaData())) }

        // Assert
        assertThat(reports.list.size, equalTo(1))
    }

    @Test
    fun `Suppressed rejections are counted and reported by the next line`() {
        // Arrange
        val oocut = diagnostics()
        repeat(10) { oocut.record(preRequest(), stored(storedMetaData())) }

        // Act
        currentTime += WINDOW_MILLIS
        oocut.record(preRequest(), stored(storedMetaData()))

        // Assert
        // Ten rejections happened before the window turned, of which the first was reported with a count of one.
        // The eleventh opens the next window and accounts for the nine which stayed silent, plus itself.
        assertThat(reports.list.size, equalTo(2))
        assertThat(report(0).getLong("n"), equalTo(1L))
        assertThat(report(1).getLong("n"), equalTo(10L))
    }

    @Test
    fun `Reported counts account for every rejection which a later line closed off`() {
        // Arrange
        val oocut = diagnostics()
        val rejections = 250
        repeat(rejections) {
            oocut.record(preRequest(), stored(storedMetaData()))
            currentTime += WINDOW_MILLIS / 10
        }

        // Act
        // Whatever the last window still holds is only accounted for by the next line about this upload, so one
        // further rejection is needed before the reported counts can be complete.
        oocut.record(preRequest(), stored(storedMetaData()))

        // Assert
        val reported = reports.list.indices.sumOf { report(it).getLong("n") }
        assertThat(reported, equalTo(rejections + 1L))
    }

    @Test
    fun `Rejections still held by an open window are not reported yet`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        repeat(10) { oocut.record(preRequest(), stored(storedMetaData())) }

        // Assert
        // Only the rejection which opened the window has been reported; the nine behind it wait for the next line.
        val reported = reports.list.indices.sumOf { report(it).getLong("n") }
        assertThat(reported, equalTo(1L))
    }

    @Test
    fun `An attachment is reported separately from the measurement it belongs to`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest(), stored(storedMetaData()))
        oocut.record(preRequest(attachmentId = 7L), stored(storedMetaData()))

        // Assert
        assertThat(reports.list.size, equalTo(2))
        assertThat(report(1).getLong("aid"), equalTo(7L))
    }

    @Test
    fun `Report says so when nothing is stored after all`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest()) { Future.succeededFuture(null) }

        // Assert
        val report = singleReport()
        assertThat(report.getString("diff"), equalTo("db-missing"))
        assertThat(report.getJsonObject("db"), equalTo(null))
    }

    @Test
    fun `Report says so when the stored upload cannot be read`() {
        // Arrange
        val oocut = diagnostics()

        // Act
        oocut.record(preRequest()) { Future.failedFuture(IllegalStateException("no connection")) }

        // Assert
        val report = singleReport()
        assertThat(report.getString("diff"), equalTo("db-error"))
        assertThat(report.getString("err"), equalTo("no connection"))
    }

    @Test
    fun `Nothing is reported and nothing is loaded while the diagnostic is switched off`() {
        // Arrange
        diagnosticsLogger.level = Level.OFF
        val oocut = diagnostics()
        var loaded = false

        // Act
        oocut.record(preRequest()) {
            loaded = true
            Future.succeededFuture(storedMetaData())
        }

        // Assert
        assertThat(reports.list.size, equalTo(0))
        assertThat(loaded, equalTo(false))
    }

    @Test
    fun `The stored upload is loaded only for rejections which are actually reported`() {
        // Arrange
        val oocut = diagnostics()
        var loads = 0

        // Act
        repeat(5) {
            oocut.record(preRequest()) {
                loads++
                Future.succeededFuture(storedMetaData())
            }
        }

        // Assert
        assertThat(loads, equalTo(1))
    }

    /**
     * The object under test, reading the time this test controls.
     */
    private fun diagnostics() = ConflictDiagnostics(windowMillis = WINDOW_MILLIS, now = { currentTime })

    /**
     * The metadata of a pre-request, as it arrives from a client: every value a string.
     */
    private fun preRequest(attachmentId: Long? = null): JsonObject {
        val metaData = JsonObject()
            .put(FormAttributes.DEVICE_ID.value, DEVICE_ID)
            .put(FormAttributes.MEASUREMENT_ID.value, MEASUREMENT_ID.toString())
            .put(FormAttributes.DEVICE_TYPE.value, "SM-A546B")
            .put(FormAttributes.OS_VERSION.value, "Android 14")
            .put(FormAttributes.APPLICATION_VERSION.value, "4.0.25")
            .put(FormAttributes.FORMAT_VERSION.value, FORMAT_VERSION.toString())
            .put(FormAttributes.LENGTH.value, LENGTH.toString())
            .put(FormAttributes.LOCATION_COUNT.value, LOCATION_COUNT.toString())
            .put(FormAttributes.START_LOCATION_TS.value, START_TIME.toString())
            .put(FormAttributes.END_LOCATION_TS.value, END_TIME.toString())
            .put(FormAttributes.MODALITY.value, "BICYCLE")
        if (attachmentId != null) {
            metaData.put(FormAttributes.ATTACHMENT_ID.value, attachmentId.toString())
        }
        return metaData
    }

    /**
     * The metadata of an already stored upload, matching [preRequest] unless a parameter says otherwise.
     */
    private fun storedMetaData(
        locationCount: Long = LOCATION_COUNT,
        length: Double = LENGTH,
        startTime: Long = START_TIME,
        endTime: Long = END_TIME,
        applicationVersion: String = "4.0.25",
    ) = StoredMetaData(
        deviceType = "SM-A546B",
        operatingSystemVersion = "Android 14",
        applicationVersion = applicationVersion,
        formatVersion = FORMAT_VERSION,
        length = length,
        locationCount = locationCount,
        startLocationTimestamp = startTime,
        endLocationTimestamp = endTime,
        modality = "BICYCLE",
        uploadDate = "2026-08-01T10:11:12Z",
    )

    /**
     * Provides [metaData] the way the storage would.
     */
    private fun stored(metaData: StoredMetaData): () -> Future<StoredMetaData?> =
        { Future.succeededFuture(metaData) }

    /**
     * The only report written during a test, parsed back from the log line.
     */
    private fun singleReport(): JsonObject {
        assertThat(reports.list.size, equalTo(1))
        return report(0)
    }

    /**
     * A report written during a test, parsed back from the log line.
     */
    private fun report(index: Int) = JsonObject(reports.list[index].message)

    companion object {
        /**
         * A window short enough to keep the tests readable, crossed by advancing the injected clock.
         */
        private const val WINDOW_MILLIS = 60_000L

        /**
         * The device the simulated client uploads from.
         */
        private const val DEVICE_ID = "e2e05e2f-4b0e-4b6e-9c48-1b5b47f4a1cd"

        /**
         * The measurement the simulated client keeps re-offering.
         */
        private const val MEASUREMENT_ID = 4711L

        /**
         * The number of locations the simulated client reports.
         */
        private const val LOCATION_COUNT = 812L

        /**
         * The track length the simulated client reports, in meters.
         */
        private const val LENGTH = 9321.4

        /**
         * The timestamp of the first location of the simulated measurement.
         */
        private const val START_TIME = 1_755_000_000_000L

        /**
         * The timestamp of the last location of the simulated measurement.
         */
        private const val END_TIME = 1_755_003_600_000L

        /**
         * The transfer file format the simulated client uses.
         */
        private const val FORMAT_VERSION = 3
    }
}
