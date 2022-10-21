package de.cyface.collector.handler

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.storage.DataStorageService
import de.cyface.collector.storage.Status
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.streams.Pipe
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MeasurementHandlerTest {

    @Mock
    lateinit var mockStorageService: DataStorageService

    @Mock
    lateinit var mockResponse: HttpServerResponse

    @Mock
    lateinit var mockPipe: Pipe<Buffer>

    @Mock
    lateinit var mockUser: User

    private lateinit var mockRequest: HttpServerRequest

    private lateinit var mockSession: Session

    private lateinit var mockRoutingContext: RoutingContext

    private lateinit var headers: MultiMap

    private lateinit var oocut: MeasurementHandler

    @BeforeEach
    fun setUp() {
        headers = MultiMap.caseInsensitiveMultiMap()
        val deviceIdentifier = UUID.randomUUID()
        val measurementIdentifier = 1L
        metadata(headers, deviceIdentifier, measurementIdentifier)

        val payloadLimit = 10L

        oocut = MeasurementHandler(mockStorageService, payloadLimit)

        mockRequest = mock {
            on { headers() } doReturn headers
            on { getHeader(any()) } doAnswer { getHeaderCall ->
                headers.get(getHeaderCall.getArgument(0, String::class.java))
            }
            on { pipe() } doReturn mockPipe
        }

        mockSession = mock {
            on { get<String>("measurement-id") } doReturn measurementIdentifier.toString()
            on { get<String>("device-id") } doReturn deviceIdentifier.toString()
        }
        mockRoutingContext = mock {
            on { request() } doReturn mockRequest
            on { get<User>("logged-in-user") } doReturn mockUser
            on { session() } doReturn mockSession
        }
    }

    @Test
    fun `Fail resume upload with no previous data`() {
        // Arrange
        // Set from index to something larger than zero.
        headers.add("content-range", "bytes 5-9/5")
        headers.add("content-length", "5")
        whenever(mockRoutingContext.response()).doReturn(mockResponse)

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        verify(mockResponse).statusCode = 404
    }

    @Test
    fun `Test resume upload from existing data`() {
        // Arrange
        val uploadIdentifier = UUID.randomUUID()
        whenever(mockSession.get<UUID>("upload-path")).thenReturn(uploadIdentifier)
        headers.add("content-range", "bytes 5-9/5")
        headers.add("content-length", "5")
        val mockBytesUploadedCall = mock<Future<Long>> {}
        whenever(mockStorageService.bytesUploaded(any())).thenReturn(mockBytesUploadedCall)
        val mockStoreCall = mock<Future<Status>> {}
        whenever(mockStorageService.store(any(), any(), any(), any(), any())).thenReturn(mockStoreCall)

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        argumentCaptor<Handler<Long>>().apply {
            verify(mockBytesUploadedCall).onSuccess(capture())

            firstValue.handle(5L)

            // I've found no way to test further than this, without writing an integration test
            verify(mockStoreCall).onSuccess(any())
        }
    }

    @Test
    fun `Fail if client tries to start new upload but temporary data exists`() {
        // Arrange
        val uploadIdentifier = UUID.randomUUID()
        whenever(mockSession.get<UUID>("upload-path")).thenReturn(uploadIdentifier)
        headers.add("content-range", "bytes 0-9/10")
        headers.add("content-length", "10")
        whenever(mockRoutingContext.response()).doReturn(mockResponse)

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        verify(mockResponse).statusCode = 404
    }

    @Test
    fun `Test start happy path upload`() {
        // Arrange
        headers.add("content-range", "bytes 0-9/10")
        headers.add("content-length", "10")

        // Act
        oocut.handle(mockRoutingContext)

        // Assert
        verify(mockStorageService).store(eq(mockPipe), eq(mockUser), eq(ContentRange(0L, 9L, 10L)), any(), any())
    }

    fun metadata(requestHeaders: MultiMap, deviceIdentifier: UUID, measurementIdentifier: Long) {
        requestHeaders.add("deviceType", "testDeviceType")
        requestHeaders.add("appVersion", "testAppVersion")
        requestHeaders.add("startLocLat", "50.2872300402633")
        requestHeaders.add("locationCount", "2")
        requestHeaders.add("startLocLon", "9.185135040263333")
        requestHeaders.add("length", "0.0")
        requestHeaders.add("endLocLon", "9.492934709138925")
        requestHeaders.add("deviceId", deviceIdentifier.toString())
        requestHeaders.add("endLocTS", "1503055141001")
        requestHeaders.add("modality", "BICYCLE")
        requestHeaders.add("startLocTS", "1503055141000")
        requestHeaders.add("endLocLat", "50.59502970913889")
        requestHeaders.add("osVersion", "testOsVersion")
        requestHeaders.add("measurementId", measurementIdentifier.toString())
        requestHeaders.add("formatVersion", "3")
    }
}
