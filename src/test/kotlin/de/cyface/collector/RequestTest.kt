/*
 * Copyright 2018-2025 Cyface GmbH
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
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Stream

/**
 * This tests the REST-API provided by the collector and used to upload the data to the server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
@ExtendWith(VertxExtension::class)
class RequestTest {
    /**
     * A client used to connect with the Cyface Data Collector.
     */
    private lateinit var collectorClient: DataCollectorApi

    /**
     * A Mongo database lifecycle handler. This provides the test with the capabilities to run and shutdown a Mongo
     * database for testing purposes.
     */
    private var mongoTest: MongoTest = MongoTest()

    /**
     * Deploys the [CollectorApiVerticle] in a test context.
     *
     * @param vertx A `Vertx` instance used for deploying the verticle
     */
    @BeforeEach
    fun deployVerticle(vertx: Vertx) = runBlocking {
        collectorClient = DataCollectorApi(140_000L, vertx)
        mongoTest = MongoTest()
        mongoTest.setUpMongoDatabase()
        collectorClient.runCollectorApi(vertx, mongoTest, MockedHandlerBuilder())
    }

    /**
     * Finishes the mongo database after this test has finished.
     */
    @AfterEach
    fun shutdown() {
        mongoTest.stopMongoDb()
    }

    /**
     * Tests the correct workings of accessing the API specification.
     *
     * @param context The test context for running `Vertx` under test
     */
    @MethodSource("testParameters")
    @ParameterizedTest
    fun testGetRoot_returnsApiSpecification(apiEndpoint: String?, context: VertxTestContext) {
        collectorClient.client.get(collectorClient.port, TEST_HOST, apiEndpoint)
            .send(context.succeeding<HttpResponse<Buffer?>?>(Handler { response: HttpResponse<Buffer?>? ->
                context.verify(VertxTestContext.ExecutionBlock {
                    MatcherAssert.assertThat<Int?>(
                        "Invalid HTTP status code on request for API specification.", response!!.statusCode(),
                        Matchers.`is`<Int?>(200)
                    )
                    val body = response.bodyAsString()
                    val expectedContent = "<title>Cyface Data Collector</title>"
                    val assertBodyReason = "Request for API specification seems to be missing a valid body."
                    MatcherAssert.assertThat<String?>(assertBodyReason, body, Matchers.containsString(expectedContent))
                    context.completeNow()
                })
            }))
    }

    /**
     * Tests that the default error handler correctly returns 404 status as response for a non-valid request.
     *
     * @param ctx The test context for running `Vertx` under test
     */
    @MethodSource("testParameters")
    @ParameterizedTest
    fun testGetUnknownResource_Returns404(apiEndpoint: String?, ctx: VertxTestContext) {
        collectorClient.client.post(collectorClient.port, TEST_HOST, apiEndpoint + "garbage")
            .send(ctx.succeeding<HttpResponse<Buffer?>?>(Handler { response: HttpResponse<Buffer?>? ->
                ctx.verify(VertxTestContext.ExecutionBlock {
                    MatcherAssert.assertThat<Int?>(
                        "Invalid HTTP status code on requesting invalid resource!", response!!.statusCode(),
                        Matchers.`is`<Int?>(404)
                    )
                    ctx.completeNow()
                })
            }))
    }

    companion object {
        /**
         * The logger used for objects of this class. Configure it by either changing values in
         * `src/main/resources/logback.xml` or in `src/test/resources/logback-test.xml`.
         */
        @Suppress("unused")
        private val LOGGER: Logger = LoggerFactory.getLogger(RequestTest::class.java)

        /**
         * The hostname used to send test requests to.
         */
        private const val TEST_HOST = "localhost"

        /**
         * Parameters used in tests above - see @MethodSource("testParameters").
         *
         * @return Provide the parameters for each run of the parameterized test
         */
        @JvmStatic
        fun testParameters(): Stream<String?> {
            return Stream.of<String?>("/")
        }
    }
}
