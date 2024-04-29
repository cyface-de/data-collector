package de.cyface.collector.storage.cloud

import de.cyface.collector.auth.MockedHandlerBuilder
import de.cyface.collector.configuration.GoogleCloudStorageType
import de.cyface.collector.verticle.CollectorApiVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.MultiMap
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This tests the whole roundtrip of uploading a picture to a data collector using the Google Cloud Storage service.
 *
 * To get this test running an accessible Mongo database and Google Cloud Storage service is required.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
@ExtendWith(VertxExtension::class)
class UploadPictureIT {
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
        //val metricsEnabled = false
        val storageType = GoogleCloudStorageType(
            collectionName = "cyface",
            projectIdentifier = "object-storage-412713",
            bucketName = "cyface",
            credentialsFile = "/Users/muthmann/.config/gcloud/application_default_credentials.json"
        )
        //val authType = AuthType.Mocked
        /*val oauthConfig = Configuration.OAuthConfig(
            callback = URL("http://localhost:8080/callback"),
            client = "collector",
            secret = "**********",
            site = URL("http://localhost:8081/realms/{tenant}"),
            tenant = "rfr",
        )*/

        val oocut = CollectorApiVerticle(
            MockedHandlerBuilder(),
            httpPort,
            measurementPayloadLimit,
            uploadExpiration,
            storageType,
            mongoDb

        )
        val deviceIdentifier = UUID.randomUUID()
        val measurementIdentifier = 0L
        val webClient = WebClient.create(vertx)

        val verticleDeployedFuture = vertx.deployVerticle(oocut)

        verticleDeployedFuture.onComplete(testContext.succeeding {
            // Make the pre request
            val preRequestFuture = onSuccessfullyDeployed(
                webClient,
                httpHost,
                httpPort,
                deviceIdentifier,
                measurementIdentifier
            )
            preRequestFuture.onComplete(testContext.succeeding {
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
                    measurementIdentifier
                )
                uploadResponseFuture.onComplete(testContext.succeeding {
                    testContext.verify {
                        assertEquals(201, it.result().statusCode())
                    }
                    testContext.completeNow()
                })
            })
        })
    }

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

        } catch(error: Exception) {
            promise.fail(error)
        }

        return promise.future()
    }

    private fun onPreRequestSuccessful(
        webClient: WebClient,
        location: URL,
        deviceIdentifier: UUID,
        measurementIdentifier: Long
    ): Future<AsyncResult<HttpResponse<Buffer>>> {
        val promise = Promise.promise<AsyncResult<HttpResponse<Buffer>>>()
        val request = webClient.put(location.port, location.host, location.path)
        // val data = data()
        val data = imageData()

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

    private fun data(): ByteArray {//this.javaClass.getResource("/example-image.jpg").readBytes()
        val ret = mutableListOf<Byte>()
        (0..500000).forEach {
            ret.add(it.toByte())
        }
        return ret.toByteArray()
    }

    private fun imageData(): ByteArray {
       return this.javaClass.getResource("/example-image.jpg").readBytes()
    }
}
