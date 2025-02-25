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
package de.cyface.collector.commons

import de.cyface.collector.auth.AuthHandlerBuilder
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.MeasurementMetaData
import de.cyface.collector.storage.gridfs.GridFsDao
import de.cyface.collector.storage.gridfs.GridFsStorageServiceBuilder
import de.cyface.collector.verticle.CollectorApiVerticle
import de.cyface.collector.verticle.CollectorApiVerticleTest
import de.cyface.collector.verticle.ServerConfiguration
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.coAwait
import java.net.URL
import java.nio.file.Path
import java.util.Locale
import kotlin.text.matches

/**
 * A client providing capabilities for tests to communicate with a Cyface Data Collector server.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @property measurementLimit The maximal number of `Byte`s which may be uploaded.
 */
class DataCollectorApi(private val measurementLimit: Long, vertx: Vertx) {

    /**
     * The port the server is reachable at.
     */
    @Transient
    var port: Int = 0
        private set

    /**
     * The client used to talk to this API.
     */
    val client: WebClient = WebClient.create(vertx)

    /**
     * Starts a test Cyface Data Collector and creates a Vert.x `WebClient` usable to access a Cyface Data
     * Collector.
     *
     * @param vertx The `Vertx` instance to start and access the server.
     * @param mongoTest A representation of the in-memory test database.
     * @param authHandlerBuilder Provide an authentication method for the server.
     * @return A completely configured `WebClient` capable of accessing the started Cyface Data Collector.
     */
    suspend fun runCollectorApi(
        vertx: Vertx,
        mongoTest: MongoTest,
        authHandlerBuilder: AuthHandlerBuilder
    ) {
        port = CollectorApiVerticleTest.findFreePort()

        val mongoDbConfig = mongoTest.clientConfiguration()
        val mongoClient = MongoClient.createShared(vertx, mongoDbConfig)

        val config = ConfigurationFactory.port(port)
            .mongoDbConfig(mongoDbConfig)
            .measurementLimit(measurementLimit)
            .mockedConfiguration()

        val collectorVerticle = CollectorApiVerticle(
            authHandlerBuilder,
            ServerConfiguration(
                config.httpPort,
                config.httpPath,
                config.measurementPayloadLimit,
                config.uploadExpiration
            ),
            GridFsStorageServiceBuilder(
                GridFsDao(mongoClient),
                vertx.fileSystem(),
                Path.of("upload-folder")
            )
        )
        vertx.deployVerticle(collectorVerticle).coAwait()
    }

    /**
     * Sends a pre-request for an upload using an authenticated request. You may listen to the completion of this
     * request using any of the provided handlers.
     */
    suspend fun preRequest(
        measurementIdentifier: MeasurementIdentifier,
        measurementMetaData: MeasurementMetaData,
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData,
        authToken: String = "eyTestToken",
    ): HttpResponse<Buffer?> {
        val metaDataBody = toJson(measurementIdentifier, measurementMetaData, deviceMetaData, applicationMetaData)
        return sendPreRequest(authToken, metaDataBody.toString().length, metaDataBody)
    }

    /**
     * Send a prerequest, but do not calculate contenSize from the actual body size.
     * This is useful, if one needs to fake body size to simulate sinister clients.
     */
    suspend fun preRequest(
        measurementIdentifier: MeasurementIdentifier,
        measurementMetaData: MeasurementMetaData,
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData,
        contentSize: Int,
        authToken: String = "eyTestToken",
    ): HttpResponse<Buffer?> {
        // Assemble payload (metaData)
        val metaDataBody = toJson(measurementIdentifier, measurementMetaData, deviceMetaData, applicationMetaData)

        // Send Pre-Request
        return sendPreRequest(authToken, contentSize, metaDataBody)
    }

    /**
     * Convert metadata to a [JsonObject] as is required as the body of a pre request.
     */
    private fun toJson(
        measurementIdentifier: MeasurementIdentifier,
        measurementMetaData: MeasurementMetaData,
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData
    ): JsonObject {
        return json {
            obj(
                "deviceType" to deviceMetaData.deviceType, //"testDeviceType")
                "appVersion" to applicationMetaData.applicationVersion, //"testAppVersion")
                "startLocLat" to measurementMetaData.startLocation?.latitude, //TEST_MEASUREMENT_START_LOCATION_LAT)
                "locationCount" to measurementMetaData.locationCount,
                "startLocLon" to measurementMetaData.startLocation?.longitude, //TEST_MEASUREMENT_START_LOCATION_LON)
                "length" to measurementMetaData.length,//"0.0")
                "endLocLon" to measurementMetaData.endLocation?.longitude, //TEST_MEASUREMENT_END_LOCATION_LON)
                "deviceId" to measurementIdentifier.deviceIdentifier.toString(), //deviceIdentifier)
                "endLocTS" to measurementMetaData.endLocation?.timestamp, //"1503055141001")
                "modality" to measurementMetaData.modality, //"BICYCLE")
                "startLocTS" to measurementMetaData.startLocation?.timestamp, //"1503055141000")
                "endLocLat" to measurementMetaData.endLocation?.latitude, //TEST_MEASUREMENT_END_LOCATION_LAT)
                "osVersion" to deviceMetaData.operatingSystemVersion, //"testOsVersion")
                "measurementId" to measurementIdentifier.measurementIdentifier, //measurementIdentifier)
                "formatVersion" to applicationMetaData.formatVersion,//"3")
            )
        }
    }

    /**
     * Send a pre request to the Cyface Data Collector service.
     */
    private suspend fun sendPreRequest(
        authToken: String,
        contentLength: Int,
        metaDataBody: JsonObject
    ): HttpResponse<Buffer?> {
        val builder = client.post(port, "localhost", "/measurements?uploadType=resumable")
        builder.putHeader("Authorization", "Bearer $authToken")
        builder.putHeader("Accept-Encoding", "gzip")
        builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        builder.putHeader("x-upload-content-type", "application/octet-stream")
        builder.putHeader("x-upload-content-length", contentLength.toString())
        builder.putHeader("Content-Type", "application/json; charset=UTF-8")
        builder.putHeader("Host", "10.0.2.2:8081")
        builder.putHeader("Connection", "Keep-Alive")
        builder.putHeader("content-length", "406") // random value, must be correct for the upload
        return builder.sendJson(metaDataBody).coAwait()
    }

    /**
     * Send a pre request to the Cyface Collector service and return only the URL for uploading the actual data.
     */
    suspend fun preRequestAndReturnLocation(
        measurementIdentifier: MeasurementIdentifier,
        measurementMetaData: MeasurementMetaData,
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData,
        contentSize: Int,
        authToken: String = "eyTestToken",
    ): URL {
        val res = preRequest(
            measurementIdentifier,
            measurementMetaData,
            deviceMetaData,
            applicationMetaData,
            contentSize,
            authToken,
        )
        return extractLocation(res)
    }

    /**
     * Runs a pre-request against this API and provides the location to send the upload request to.
     */
    suspend fun preRequestAndReturnLocation(
        measurementIdentifier: MeasurementIdentifier,
        measurementMetaData: MeasurementMetaData,
        deviceMetaData: DeviceMetaData,
        applicationMetaData: ApplicationMetaData,
        authToken: String = "eyTestToken"
    ): URL {
        val res = preRequest(measurementIdentifier, measurementMetaData, deviceMetaData, applicationMetaData, authToken)
        return extractLocation(res)
    }

    /**
     * Extract the location header from a pre request response.
     */
    private fun extractLocation(response: HttpResponse<Buffer?>): URL {
        require(response.statusCode() == 200) { "Pre-request failed with status code ${response.statusCode()}" }
        val location = response.getHeader("Location")
        requireNotNull(location) { "Missing HTTP Location header in pre-request response!" }
        val locationPattern = "http://10\\.0\\.2\\.2:8081/measurements/\\([a-z0-9]{32}\\)/"
        require(location.matches(locationPattern.toRegex())) { "Wrong HTTP Location header on pre-request!" }
        val locationUrl = URL(location)
        return locationUrl
    }

    /**
     * This method is necessary to test erronous uploads.
     */
    suspend fun upload(
        vertx: Vertx,
        testFileResourceName: String,
        length: String,
        binarySize: Int,
        requestUri: URL,
        @Suppress("SameParameterValue") from: Long,
        to: Long,
        total: Long,
        deviceIdentifier: String,
        measurementIdentifier: String,
        authToken: String = "eyTestToken",
    ): HttpResponse<Buffer?> {
        val testFileResource = this.javaClass.getResource(testFileResourceName)
        requireNotNull(testFileResource)

        // Upload data
        val path = requestUri.path
        val builder = client.put(port, "localhost", path)
        val jwtBearer = "Bearer $authToken"
        builder.putHeader("Authorization", jwtBearer)
        builder.putHeader("Accept-Encoding", "gzip")
        builder.putHeader("Content-Range", String.format(Locale.ENGLISH, "bytes %d-%d/%d", from, to, total))
        builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        builder.putHeader("Content-Type", "application/octet-stream")
        builder.putHeader("Host", "localhost:" + port)
        builder.putHeader("Connection", "Keep-Alive")
        // If the binary length is not set correctly, the connection is closed and no handler called
        // [DAT-735]
        builder.putHeader("content-length", binarySize.toString())
        // metaData
        builder.putHeader("deviceType", "testDeviceType")
        builder.putHeader("appVersion", "testAppVersion")
        builder.putHeader("startLocLat", "50.2872300402633")
        builder.putHeader("locationCount", "2")
        builder.putHeader("startLocLon", "9.185135040263333")
        builder.putHeader("length", length)
        builder.putHeader("endLocLon", "9.492934709138925")
        builder.putHeader("deviceId", deviceIdentifier)
        builder.putHeader("endLocTS", "1503055141001")
        builder.putHeader("modality", "BICYCLE")
        builder.putHeader("startLocTS", "1503055141000")
        builder.putHeader("endLocLat", "50.59502970913889")
        builder.putHeader("osVersion", "testOsVersion")
        builder.putHeader("measurementId", measurementIdentifier)
        builder.putHeader("formatVersion", "3")
        val file = vertx.fileSystem().openBlocking(testFileResource.file, OpenOptions())
        return builder.sendStream(file).coAwait()
    }

    /**
     * Uploads the provided file using an authenticated request. You may listen to the completion of this upload using
     * any of the provided handlers.
     *
     * @param vertx The Vertx instance used to access the local file system, to read the test data.
     * @param testFileResourceName The Java resource name of a file to upload.
     * @param length the meter-length of the track
     * @param binarySize number of bytes in the binary to upload
     * @param requestUri The URI to upload the data to.
     * @param from The byte index to start the upload at.
     * @param to The byte index to upload to.
     * @param total The total amount of bytes to upload.
     * @param measurementIdentifier The identifier of the measurement to upload.
     * This should fit the one provided with the corresponding pre request.
     * @param authToken The token to authenticate with.
     */
    suspend fun upload(
        vertx: Vertx,
        testFileResourceName: String,
        length: String,
        binarySize: Int,
        requestUri: URL,
        @Suppress("SameParameterValue") from: Long,
        to: Long,
        total: Long,
        measurementIdentifier: MeasurementIdentifier,
        authToken: String = "eyTestToken"
    ): HttpResponse<Buffer?> {
        return upload(
            vertx,
            testFileResourceName,
            length,
            binarySize,
            requestUri,
            from,
            to,
            total,
            measurementIdentifier.deviceIdentifier.toString(),
            measurementIdentifier.measurementIdentifier.toString(),
            authToken
        )
    }

    /**
     * Send a status request asynchonously to this Collector API.
     */
    suspend fun uploadStatus(
        requestUri: URL,
        contentRange: String,
        measurementIdentifier: MeasurementIdentifier,
        authToken: String = "eyTestToken"
    ): HttpResponse<Buffer?> {
        // Send empty PUT request to ask where to continue the upload
        val path = requestUri.path
        val builder = client.put(port, "localhost", path)
        val jwtBearer = "Bearer $authToken"
        builder.putHeader("Authorization", jwtBearer)
        builder.putHeader("Accept-Encoding", "gzip")
        builder.putHeader("User-Agent", "Google-HTTP-Java-Client/1.39.2 (gzip)")
        builder.putHeader("Content-Type", "application/octet-stream") // really?
        builder.putHeader("Host", "localhost:" + port)
        builder.putHeader("Connection", "Keep-Alive")
        // empty body
        builder.putHeader("content-length", "0")
        // ask where to continue
        builder.putHeader("Content-Range", contentRange)
        // metaData
        builder.putHeader("deviceType", "testDeviceType")
        builder.putHeader("appVersion", "testAppVersion")
        builder.putHeader("startLocLat", "50.2872300402633")
        builder.putHeader("locationCount", "2")
        builder.putHeader("startLocLon", "9.185135040263333")
        builder.putHeader("length", "0.0")
        builder.putHeader("endLocLon", "9.492934709138925")
        builder.putHeader("deviceId", measurementIdentifier.deviceIdentifier.toString())
        builder.putHeader("endLocTS", "1503055141001")
        builder.putHeader("modality", "BICYCLE")
        builder.putHeader("startLocTS", "1503055141000")
        builder.putHeader("endLocLat", "50.59502970913889")
        builder.putHeader("osVersion", "testOsVersion")
        builder.putHeader("measurementId", measurementIdentifier.measurementIdentifier.toString())
        builder.putHeader("formatVersion", "3")
        return builder.send().coAwait()
    }
}
