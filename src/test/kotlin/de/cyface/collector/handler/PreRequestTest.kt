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

@ExtendWith(MockitoExtension::class)
class PreRequestTest {

    @Mock
    lateinit var mockStorageService: DataStorageService

    @Mock
    lateinit var mockRoutingContext: RoutingContext

    @Mock
    lateinit var mockResponse: HttpServerResponse

    @Mock
    lateinit var mockBody: RequestBody

    @Mock
    lateinit var mockRequest: HttpServerRequest

    @Mock
    lateinit var mockSession: Session

    lateinit var deviceId: String

    lateinit var oocut: PreRequestHandler

    @BeforeEach
    fun setUp() {
        deviceId = UUID.randomUUID().toString()
        whenever(mockRoutingContext.request()).thenReturn(mockRequest)
        whenever(mockRoutingContext.session()).thenReturn(mockSession)

        whenever(mockRoutingContext.body()).thenReturn(mockBody)
        whenever(mockBody.asJsonObject()).thenReturn(preRequestBody(deviceId))
        oocut = PreRequestHandler(mockStorageService, 100L)
    }

    @Test
    fun `Successful PreRequest Happy Path returns status 200`() {
        // Arrange
        whenever(mockRoutingContext.response()).thenReturn(mockResponse)
        whenever(mockResponse.putHeader(any(String::class.java), any(String::class.java))).thenReturn(mockResponse)
        whenever(mockResponse.setStatusCode(anyInt())).thenReturn(mockResponse)
        whenever(mockRequest.headers()).thenReturn(preRequestHeaders(50))
        val mockIsStoredResult = Mockito.mock(Future::class.java)
        whenever(mockStorageService.isStored(deviceId, 1L)).thenReturn(mockIsStoredResult as Future<Boolean>)
        val captor = ArgumentCaptor.forClass(Handler::class.java)
        whenever(mockRequest.absoluteURI()).thenReturn("https://localhost:8080/api/v3/measurements/(some-uuid)")
        whenever(mockSession.id()).thenReturn("mock-session")

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        verify(mockIsStoredResult).onSuccess(captor.capture() as Handler<Boolean>?)
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
        verify(mockRoutingContext).fail(Mockito.eq(422), Mockito.any(PayloadTooLarge::class.java))
    }

    private fun preRequestBody(deviceId : String): JsonObject {
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