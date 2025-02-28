/*
 * Copyright 2021-2025 Cyface GmbH
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
package de.cyface.collector

import de.cyface.collector.auth.MockedHandlerBuilder
import de.cyface.collector.commons.DataCollectorApi
import de.cyface.collector.commons.MongoTest
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.verticle.CollectorApiVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException
import java.util.UUID

/**
 * Tests that uploading measurements to the Cyface API works as expected.
 *
 * @author Armin Schnabel
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
class FileUploadTooLargeTest {
    /**
     * A client used to connect with the Cyface Data Collector.
     */
    private lateinit var collectorApi: DataCollectorApi

    /**
     * The measurement identifier used for the test measurement. The actual value does not matter that much. It
     * simulates a device wide unique identifier.
     */
    private val measurementIdentifier = MeasurementIdentifier(UUID.randomUUID(), 1L)

    /**
     * Fixture data.
     */
    private val measurementMetaData = MeasurementMetaData(
        length = 0.0,
        locationCount = 2,
        startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
        endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
        modality = "BICYCLE"
    )

    /**
     * Fixture data.
     */
    private val deviceMetaData = DeviceMetaData(
        deviceType = "iPhone",
        operatingSystemVersion = "14.0",
    )

    /**
     * Fixture data.
     */
    private val applicationMetaData = ApplicationMetaData(
        applicationVersion = "1.0",
        formatVersion = 3,
    )

    /**
     * A Mongo database lifecycle handler. This provides the test with the capabilities to run and shutdown a Mongo
     * database for testing purposes.
     */
    private val mongoTest: MongoTest = MongoTest()

    /**
     * A very large payload size, used to test how the system reacts to large uploads.
     */
    private val uploadSize = 134697

    /**
     * Deploys the [de.cyface.collector.verticle.CollectorApiVerticle] in a test context.
     *
     * @param vertx The `Vertx` instance to deploy the verticle to
     * @throws IOException Fails the test if anything unexpected goes wrong
     */
    @Throws(IOException::class)
    private suspend fun deployVerticle(vertx: Vertx) {
        @Suppress("ForbiddenComment")
        // Set maximal payload size to 1 KB (test upload is 130 KB)
        collectorApi = DataCollectorApi(CollectorApiVerticle.BYTES_IN_ONE_KILOBYTE, vertx)
        mongoTest.setUpMongoDatabase()
        collectorApi.runCollectorApi(vertx, mongoTest, MockedHandlerBuilder())
    }

    /**
     * Initializes the environment for each test case with a mock Mongo Database and a Vert.x set up for testing.
     *
     * @param vertx A `Vertx` instance injected to be used by this test
     * @throws IOException Fails the test on unexpected exceptions
     */
    @BeforeEach
    @Throws(IOException::class)
    fun setUp(vertx: Vertx) {
        runBlocking {
            deployVerticle(vertx)
        }
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterEach // We need a new database for each test execution or else data remains in the database.
    fun shutdown() {
        mongoTest.stopMongoDb()
    }

    /**
     * Tests that an upload with a too large payload returns a 422 error.
     */
    @Test
    fun testPreRequestWithTooLargePayload_Returns422() = runTest {
        val response = collectorApi.preRequest(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = measurementMetaData,
            deviceMetaData = deviceMetaData,
            applicationMetaData = applicationMetaData,
            13_4697,
        )

        assertThat(
            "Wrong HTTP status code when uploading unparsable meta data!",
            response.statusCode(),
            `is`(equalTo(422))
        )
    }

    /**
     * Tests that an upload with a too large payload returns a 422 error.
     *
     * @param vertx The Vert.x instance used to run this test.
     */
    @Test
    fun testUploadWithTooLargePayload_Returns422(vertx: Vertx) = runTest {
        val uploadUri = collectorApi.preRequestAndReturnLocation(
            measurementIdentifier = measurementIdentifier,
            measurementMetaData = measurementMetaData,
            deviceMetaData = deviceMetaData,
            applicationMetaData = applicationMetaData,
            1000, // fake a small upload to bypass pre-request size check
        )
        val uploadResponse = collectorApi.upload(
            vertx,
            "/iphone-neu.ccyf",
            "0.0",
            uploadSize,
            uploadUri,
            0,
            (uploadSize - 1).toLong(),
            uploadSize.toLong(),
            measurementIdentifier
        )
        assertThat(
            "Wrong HTTP status code when uploading too large payload!",
            uploadResponse.statusCode(),
            `is`(equalTo(422))
        )
    }
}
