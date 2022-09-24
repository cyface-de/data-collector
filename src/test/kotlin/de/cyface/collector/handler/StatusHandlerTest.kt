package de.cyface.collector.handler

import de.cyface.collector.storage.DataStorageService
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.http.impl.headers.HeadersMultiMap
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class StatusHandlerTest {

    @Test
    fun `Asking for Status after successful upload returns HTTP status 200`() {
        // Arrange
        val deviceIdentifier = UUID.randomUUID().toString()
        val measurementIdentifier = 1L
        val mockIsStoredFuture: Future<Boolean> = mock()
        val mockByteSizeFuture: Future<Long> = mock()
        val storageService: DataStorageService = mock {
            on { isStored(deviceIdentifier, measurementIdentifier)} doReturn mockIsStoredFuture

        }
        val mockResponse: HttpServerResponse = mock {
            on { setStatusCode(anyInt()) } doReturn it
        }
        val requestHeaders = HeadersMultiMap()
        requestHeaders.add("Authorization", "Bearer fakeToken")
        requestHeaders.add("Accept-Encoding", "gzip")
        requestHeaders.add("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        requestHeaders.add("Content-Type", "application/octet-stream") // really?
        requestHeaders.add("Host", "localhost:8080")
        requestHeaders.add("Connection", "Keep-Alive")
        // empty body
        requestHeaders.add("content-length", "0")
        // ask where to continue
        requestHeaders.add("Content-Range", "bytes */4")
        // metaData
        requestHeaders.add("deviceType", "testDeviceType")
        requestHeaders.add("appVersion", "testAppVersion")
        requestHeaders.add("startLocLat", "50.2872300402633")
        requestHeaders.add("locationCount", "2")
        requestHeaders.add("startLocLon", "9.185135040263333")
        requestHeaders.add("length", "0.0")
        requestHeaders.add("endLocLon", "9.492934709138925")
        requestHeaders.add("deviceId", deviceIdentifier)
        requestHeaders.add("endLocTS", "1503055141001")
        requestHeaders.add("modality", "BICYCLE")
        requestHeaders.add("startLocTS", "1503055141000")
        requestHeaders.add("endLocLat", "50.59502970913889")
        requestHeaders.add("osVersion", "testOsVersion")
        requestHeaders.add("measurementId", measurementIdentifier.toString())
        requestHeaders.add("formatVersion", "3")
        val mockRequest: HttpServerRequest = mock {
            on { getHeader(anyString()) } doAnswer { invocation ->
                val firstArgument: String = invocation.getArgument(0)
                requestHeaders.get(firstArgument)
            }
        }
        val mockSession: Session = mock()
        val mockRoutingContext: RoutingContext = mock {
            on { response() } doReturn mockResponse
            on { request() } doReturn mockRequest
            on { session() } doReturn mockSession
        }
        val oocut = StatusHandler(storageService)

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        argumentCaptor<Handler<Boolean>>().apply {
            verify(mockIsStoredFuture).onSuccess(capture())

            firstValue.handle(true)

            verify(mockResponse).statusCode = 200
        }
    }
}