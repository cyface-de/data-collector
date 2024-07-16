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
package de.cyface.collector.storage.cloud

import com.google.auth.oauth2.GoogleCredentials
import de.cyface.collector.auth.MockedHandlerBuilder
import de.cyface.collector.commons.MongoTest
import de.cyface.collector.configuration.GoogleCloudStorageType
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.User
import de.cyface.collector.storage.UploadMetaData
import de.cyface.collector.verticle.CollectorApiVerticle
import de.cyface.collector.verticle.ServerConfiguration
import de.cyface.uploader.DefaultUploader
import de.cyface.uploader.Result
import de.cyface.uploader.UploadProgressListener
import de.cyface.uploader.model.Measurement
import de.cyface.uploader.model.MeasurementIdentifier
import de.cyface.uploader.model.metadata.ApplicationMetaData
import de.cyface.uploader.model.metadata.AttachmentMetaData
import de.cyface.uploader.model.metadata.DeviceMetaData
import de.cyface.uploader.model.metadata.GeoLocation
import de.cyface.uploader.model.metadata.MeasurementMetaData
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This tests the whole round-trip of uploading a picture to a data collector using the Google Cloud Storage service.
 *
 * To get this test running an accessible Mongo database and Google Cloud Storage service is required.
 *
 * @author Klemens Muthmann
 */
@Disabled("This test calls the actual API without mocking calls to the Google Object Storage.")
@ExtendWith(VertxExtension::class)
@Suppress("LongMethod")
class UploadPictureIT {
    /**
     * The logger used for objects of this class. Configure it using `src/test/resources/logback-test.xml`.
     */
    private val logger = LoggerFactory.getLogger(javaClass)
    // Change the following parameters to appropriate values for this test to run.
    /**
     * Consider changing the data source by modifying the data variable inside this method.
     * For example there is an example image to upload, which can take very long or just some generated data.
     * Both are available from ``imageData`` and ``data`` methods, respectively.
     */
    val data = data() // imageData()

    /**
     * The name of the Mongo collection to store measurement meta data.
     */
    private val collectionName: String = ""

    /**
     * The Google Cloud project identifier hosting the Object Store.
     */
    private val projectIdentifier: String = ""

    /**
     * The Google Cloud Data Storage bucket used to store the test data.
     */
    private val bucketName: String = ""

    /**
     * An authentication file created using the Google cloud shell.
     * This often resides at `~/.config/gcloud/application_default_credentials.json`.
     * @see <a href="https://cloud.google.com/docs/authentication/client-libraries?hl=de">Google Cloud Documentation</a>
     */
    private val credentialsFile: String = ""

    @Test
    @Timeout(value = 960, timeUnit = TimeUnit.SECONDS)
    fun `upload an image to a data collector service using the Google Cloud Backend`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val httpHost = "localhost"
        val httpPort = 8080
        val mongoDb = JsonObject()
            .put("db_name", "cyface")
            .put("connection_string", "mongodb://localhost:27019")
            .put("data_source_name", "cyface")
        val uploadExpiration = 60000L
        val measurementPayloadLimit = 104857600L
        val storageType = GoogleCloudStorageType(
            collectionName = collectionName,
            projectIdentifier = projectIdentifier,
            bucketName = bucketName,
            credentialsFile = credentialsFile
        )

        val oocut = CollectorApiVerticle(
            MockedHandlerBuilder(),
            ServerConfiguration(
                httpPort,
                "/",
                measurementPayloadLimit,
                uploadExpiration,
                storageType,
            ),
            mongoDb

        )
        val deviceIdentifier = UUID.randomUUID()
        val measurementIdentifier = 0L
        val webClient = WebClient.create(vertx)

        val verticleDeployedFuture = vertx.deployVerticle(oocut)

        verticleDeployedFuture.onComplete(
            testContext.succeeding {
                // Make the pre request
                val preRequestFuture = onSuccessfullyDeployed(
                    webClient,
                    httpHost,
                    httpPort,
                    deviceIdentifier,
                    measurementIdentifier
                )
                preRequestFuture.onComplete(
                    testContext.succeeding {
                        val response = it.result()
                        print(response.bodyAsString())
                        testContext.verify {
                            assertEquals(200, response.statusCode())
                        }
                        // On Success make the actual upload request
                        val uploadLocation = URL(response.headers()["Location"])
                        val uploadResponseFuture = onPreRequestSuccessful(
                            webClient,
                            uploadLocation,
                            deviceIdentifier,
                            measurementIdentifier,
                            data
                        )
                        uploadResponseFuture.onComplete(
                            testContext.succeeding {
                                testContext.verify {
                                    assertEquals(201, it.result().statusCode())
                                }
                                testContext.completeNow()
                            }
                        )
                    }
                )
            }
        )
    }

    @Test
    fun `upload data using the uploader library, mirroring the implementation found on Android Phones`(
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        val mongoTest = MongoTest()
        mongoTest.setUpMongoDatabase()

        val port = 8080
        val httpEndpoint = "/"
        val uploadExpirationtimeInMillis = 60000L
        val measurementPayloadLimit = 104857600L
        val largeFileStorageType = GoogleCloudStorageType(
            collectionName = collectionName,
            projectIdentifier = projectIdentifier,
            bucketName = bucketName,
            credentialsFile = credentialsFile,
        )

        val authHandlerBuilder = MockedHandlerBuilder()
        val serverConfiguration = ServerConfiguration(
            port,
            httpEndpoint,
            measurementPayloadLimit,
            uploadExpirationtimeInMillis,
            largeFileStorageType
        )
        val mongoDatabaseConfiguration = JsonObject()
            .put("db_name", "cyface")
            .put("connection_string", "mongodb://${mongoTest.getMongoHost()}:${mongoTest.getMongoPort()}")
            .put("data_source_name", "cyface")

        val verticle = CollectorApiVerticle(
            authHandlerBuilder,
            serverConfiguration,
            mongoDatabaseConfiguration
        )

        vertx.deployVerticle(verticle).onComplete(
            testContext.succeeding {
                val uploader = DefaultUploader("http://localhost:8080/")
                val jwtToken = "eyTestToken"
                val uploadable = Measurement(
                    MeasurementIdentifier(UUID.randomUUID(), 0L),
                    DeviceMetaData("iOS17", "iPhone14,1"),
                    ApplicationMetaData("7.6.5", 3),
                    MeasurementMetaData(
                        200.0,
                        20,
                        GeoLocation(System.currentTimeMillis(), 13.711864, 51.047010),
                        GeoLocation(System.currentTimeMillis(), 13.714954, 51.050895),
                        "BICYCLE",
                    ),
                    AttachmentMetaData(0, 0, 0, 0L),
                )
                val pathToUpload = this::class.java.getResource("/example-image-enterprise.jpg")?.file
                if (pathToUpload.isNullOrEmpty()) {
                    testContext.failNow("No input file available.")
                } else {
                    val fileToUpload = File(pathToUpload)
                    val result = uploader.uploadMeasurement(
                        jwtToken,
                        uploadable,
                        fileToUpload,
                        object : UploadProgressListener {
                            override fun updatedProgress(percent: Float) {
                                print("Upload Progress: $percent")
                            }
                        }
                    )

                    testContext.verify {
                        assertEquals(
                            Result.UPLOAD_SUCCESSFUL,
                            result
                        )
                    }
                    mongoTest.stopMongoDb()
                    testContext.completeNow()
                }
            }
        )
    }

    /**
     * This tests does not start a server, but rather uploads the data directly from a file.
     */
    @Test
    @Timeout(value = 960, timeUnit = TimeUnit.SECONDS)
    fun `Test the inner workings of the GoogelCloudStorageService using a real sized image`(
        vertx: Vertx,
        vertxTestContext: VertxTestContext
    ) {
        val testUploadIdentifier = UUID.randomUUID()
        logger.debug("Simulating upload with identifier: {}", testUploadIdentifier)
        val user = mock<User>()
        val projectIdentifier = projectIdentifier
        val bucketName = bucketName
        val credentialsFile = credentialsFile
        val credentials = FileInputStream(credentialsFile).use { stream -> GoogleCredentials.fromStream(stream) }
        val dao = mock<Database> {}
        val dataStorageServiceBuilder = GoogleCloudStorageServiceBuilder(
            credentials,
            projectIdentifier,
            bucketName,
            dao,
            vertx
        )
        val createDataStorageServiceBuilderProcess = dataStorageServiceBuilder.create()

        val loadExampleProcess = vertx.fileSystem().open("example-image-enterprise.jpg", OpenOptions())

        Future
            .all(loadExampleProcess, createDataStorageServiceBuilderProcess)
            .onComplete(
                vertxTestContext.succeeding { result ->
                    val exampleFile = result.resultAt<AsyncFile>(0)
                    val dataStorageService = result.resultAt<GoogleCloudStorageService>(1)
                    val fileSize = exampleFile.sizeBlocking()
                    val measurement = de.cyface.collector.model.Measurement(
                        de.cyface.collector.model.MeasurementIdentifier(
                            UUID.randomUUID(),
                            0L,
                        ),
                        de.cyface.collector.model.metadata.DeviceMetaData(
                            operatingSystemVersion = "1",
                            deviceType = "test",
                        ),
                        de.cyface.collector.model.metadata.ApplicationMetaData(
                            applicationVersion = "1",
                            formatVersion = 3,
                        ),
                        de.cyface.collector.model.metadata.MeasurementMetaData(
                            length = 200.0,
                            locationCount = 20L,
                            startLocation = de.cyface.collector.model.metadata.GeoLocation(
                                System.currentTimeMillis(),
                                13.707209,
                                51.044796
                            ),
                            endLocation = de.cyface.collector.model.metadata.GeoLocation(
                                System.currentTimeMillis(),
                                13.718708,
                                51.051013
                            ),
                            modality = "BICYCLE",
                        ),
                        de.cyface.collector.model.metadata.AttachmentMetaData(
                            logCount = 0,
                            imageCount = 0,
                            videoCount = 0,
                            filesSize = 0L,
                        ),
                    )

                    val uploadMetaData = UploadMetaData(
                        user = user,
                        contentRange = ContentRange(0, fileSize - 1, fileSize),
                        uploadIdentifier = testUploadIdentifier,
                        uploadable = measurement
                    )

                    dataStorageService.store(exampleFile, uploadMetaData)
                        .onComplete(vertxTestContext.succeedingThenComplete())
                }
            )
    }

    /**
     * This method is called if the test has successfully deployed the Vert.x Application.
     */
    private fun onSuccessfullyDeployed(
        webClient: WebClient,
        host: String,
        port: Int,
        deviceIdentifier: UUID,
        measurementIdentifier: Long
    ): Future<AsyncResult<HttpResponse<Buffer>>> {
        val promise = Promise.promise<AsyncResult<HttpResponse<Buffer>>>()
        try {
            // Make a pre request

            val request = webClient.post(port, host, "/measurements")
            val authToken = "eyTestToken"
            request
                .putHeaders(headers(authToken, host, port))
                .sendJson(metaData(200L, deviceIdentifier, measurementIdentifier)) {
                    promise.complete(it)
                }
        } catch (error: Exception) {
            promise.fail(error)
        }

        return promise.future()
    }

    /**
     * This method is called if the test has successfully send a pre request to the test server.
     */
    private fun onPreRequestSuccessful(
        webClient: WebClient,
        location: URL,
        deviceIdentifier: UUID,
        measurementIdentifier: Long,
        data: ByteArray
    ): Future<AsyncResult<HttpResponse<Buffer>>> {
        val promise = Promise.promise<AsyncResult<HttpResponse<Buffer>>>()
        val request = webClient.put(location.port, location.host, location.path)

        try {
            request.putHeader("Content-Type", "application/octet-stream")

            request.putHeader("Accept-Encoding", "gzip")
            request.putHeader(
                "Content-Range",
                String.format(Locale.ENGLISH, "bytes %d-%d/%d", 0, data.size - 1, data.size)
            )
            request.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
            request.putHeader("Content-Type", "application/octet-stream")
            request.putHeader("Host", "${location.host}:${location.port}")
            request.putHeader("Connection", "Keep-Alive")
            // If the binary length is not set correctly, the connection is closed and no handler called
            // [DAT-735]
            request.putHeader("content-length", data.size.toString())
            // metaData
            request.putHeader("deviceType", "testDeviceType")
            request.putHeader("appVersion", "testAppVertseuqesion")
            request.putHeader("startLocLat", "50.2872300402633")
            request.putHeader("locationCount", "2")
            request.putHeader("startLocLon", "9.185135040263333")
            request.putHeader("length", data.size.toString())
            request.putHeader("endLocLon", "9.492934709138925")
            request.putHeader("deviceId", deviceIdentifier.toString())
            request.putHeader("endLocTS", "1503055141001")
            request.putHeader("modality", "BICYCLE")
            request.putHeader("startLocTS", "1503055141000")
            request.putHeader("endLocLat", "50.59502970913889")
            request.putHeader("osVersion", "testOsVersion")
            request.putHeader("measurementId", measurementIdentifier.toString())
            request.putHeader("formatVersion", "3")
        } catch (error: Exception) {
            promise.fail(error)
        }

        request.sendBuffer(Buffer.buffer(data)).onComplete {
            promise.complete(it)
        }

        return promise.future()
    }

    /**
     * Create some static meta data for the test fixture.
     */
    private fun metaData(locationCount: Long, deviceIdentifier: UUID, measurementIdentifier: Long): JsonObject {
        val metaDataBody = JsonObject()
        metaDataBody.put("deviceType", "testDeviceType")
        metaDataBody.put("appVersion", "testAppVersion")
        metaDataBody.put("startLocLat", 10.0)
        metaDataBody.put("locationCount", locationCount)
        metaDataBody.put("startLocLon", 10.0)
        metaDataBody.put("length", "0.0")
        metaDataBody.put("endLocLon", 12.0)
        metaDataBody.put("deviceId", deviceIdentifier.toString())
        metaDataBody.put("endLocTS", "1503055141001")
        metaDataBody.put("modality", "BICYCLE")
        metaDataBody.put("startLocTS", "1503055141000")
        metaDataBody.put("endLocLat", 12.0)
        metaDataBody.put("osVersion", "testOsVersion")
        metaDataBody.put("measurementId", measurementIdentifier)
        metaDataBody.put("formatVersion", "3")
        return metaDataBody
    }

    /**
     * Create the HTTP headers for the pre request.
     */
    private fun headers(authToken: String, host: String, port: Int): MultiMap {
        val ret = MultiMap.caseInsensitiveMultiMap()
        ret.add("Authorization", "Bearer $authToken")
        ret.add("Accept-Encoding", "gzip")
        ret.add("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        ret.add("x-upload-content-type", "application/octet-stream")
        ret.add("x-upload-content-length", "9522")
        ret.add("Content-Type", "application/json; charset=UTF-8")
        ret.add("Host", "$host:$port")
        ret.add("Connection", "Keep-Alive")
        ret.add("content-length", "406")
        return ret
    }

    /**
     * Create a numbered array of data points.
     * This allows us to check if the upload finished correctly and no bytes where garbled up.
     */
    private fun data(): ByteArray {
        val ret = mutableListOf<Byte>()
        (0..10_000).forEach {
            ret.add(it.toByte())
        }
        return ret.toByteArray()
    }

    @Suppress("UnusedPrivateMember")
    private fun imageData(): ByteArray {
        return this.javaClass.getResource("/example-image.jpg")?.readBytes() ?: error("Unable to load example image.")
    }
}
