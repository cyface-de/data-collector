/*
 * Copyright 2022 Cyface GmbH
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

import de.cyface.collector.handler.exception.PayloadTooLarge
import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.MultiMap
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.http.impl.headers.HeadersMultiMap
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RequestBody
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Tests for running the [PreRequestHandler] in isolation.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @since 6.11.0
 */
@ExtendWith(MockitoExtension::class)
class PreRequestTest {

    /**
     * A mocked [DataStorageService] to test against, used in absence of true data storage.
     */
    @Mock
    lateinit var mockStorageService: DataStorageService

    /**
     * The mocked Vert.x routing context, used in absence of a true [RoutingContext].
     */
    @Mock
    lateinit var mockRoutingContext: RoutingContext

    /**
     * A mocked HTTP response, which can be used to verify, that the object under test behaves as expected.
     */
    @Mock
    lateinit var mockResponse: HttpServerResponse

    /**
     * A mocked request body to get data from.
     */
    @Mock
    lateinit var mockBody: RequestBody

    /**
     * A mocked request to call methods against.
     */
    @Mock
    lateinit var mockRequest: HttpServerRequest

    /**
     * Part of the mocked Vert.x environment.
     */
    @Mock
    lateinit var mockSession: Session

    /**
     * A device identifier from the simulated device sending requests to the object under test.
     */
    private lateinit var deviceId: String

    /**
     * The object of the class under test.
     */
    private lateinit var oocut: PreRequestHandler

    @BeforeEach
    fun setUp() {
        deviceId = UUID.randomUUID().toString()
        whenever(mockRoutingContext.request()).thenReturn(mockRequest)
        whenever(mockRoutingContext.session()).thenReturn(mockSession)

        whenever(mockRoutingContext.body()).thenReturn(mockBody)
        whenever(mockBody.asJsonObject()).thenReturn(preRequestBody(deviceId))
        oocut = PreRequestHandler(mockStorageService, 100L, "/")
    }

    @Test
    fun `Successful PreRequest Happy Path returns status 200`() {
        // Arrange
        whenever(mockRoutingContext.response()).thenReturn(mockResponse)
        whenever(mockResponse.putHeader(any(String::class.java), any(String::class.java))).thenReturn(mockResponse)
        whenever(mockResponse.setStatusCode(anyInt())).thenReturn(mockResponse)
        whenever(mockRequest.headers()).thenReturn(preRequestHeaders(50))
        val mockIsStoredResult = Mockito.mock(Future::class.java)
        @Suppress("UNCHECKED_CAST")
        whenever(mockStorageService.isStored(deviceId, 1L)).thenReturn(mockIsStoredResult as Future<Boolean>)
        val captor = ArgumentCaptor.forClass(Handler::class.java)
        whenever(mockRequest.absoluteURI()).thenReturn("https://localhost:8080/api/v4/measurements/(some-uuid)")
        whenever(mockSession.id()).thenReturn("mock-session")

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        @Suppress("UNCHECKED_CAST")
        verify(mockIsStoredResult).onSuccess(captor.capture() as Handler<Boolean>?)
        @Suppress("UNCHECKED_CAST")
        (captor.value as Handler<Boolean>).handle(false)
        verify(mockResponse).statusCode = 200
    }

    @Test
    fun `PreRequest refused on large upload`() {
        // Arrange
        whenever(mockRequest.headers()).thenReturn(preRequestHeaders(134697))

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        verify(mockRoutingContext).fail(Mockito.eq(422), any(PayloadTooLarge::class.java))
    }

    private fun preRequestBody(deviceId: String): JsonObject {
        val metaDataBody = JsonObject()
        metaDataBody.put("deviceType", "testDeviceType")
        metaDataBody.put("appVersion", "testAppVersion")
        metaDataBody.put("startLocLat", 10.0)
        metaDataBody.put("locationCount", 2)
        metaDataBody.put("startLocLon", 10.0)
        metaDataBody.put("length", "0.0")
        metaDataBody.put("endLocLon", 12.0)
        metaDataBody.put("deviceId", deviceId)
        metaDataBody.put("endLocTS", "1503055141001")
        metaDataBody.put("modality", "BICYCLE")
        metaDataBody.put("startLocTS", "1503055141000")
        metaDataBody.put("endLocLat", 12.0)
        metaDataBody.put("osVersion", "testOsVersion")
        metaDataBody.put("measurementId", 1L.toString())
        metaDataBody.put("formatVersion", "3")
        return metaDataBody
    }

    private fun preRequestHeaders(size: Int): MultiMap {
        val mockHeaders = HeadersMultiMap()
        mockHeaders.add("Authorization", "Bearer invalidToken")
        mockHeaders.add("Accept-Encoding", "gzip")
        mockHeaders.add("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        mockHeaders.add("x-upload-content-type", "application/octet-stream")
        mockHeaders.add("x-upload-content-length", size.toString())
        mockHeaders.add("Content-Type", "application/json; charset=UTF-8")
        mockHeaders.add("Host", "10.0.2.2:8081")
        mockHeaders.add("Connection", "Keep-Alive")
        mockHeaders.add("content-length", "406")
        return mockHeaders
    }
}
