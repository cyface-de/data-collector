/*
 * Copyright 2022-2025 Cyface GmbH
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

import de.cyface.collector.configuration.Configuration
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * A factory for the creation of a test fixture [Configuration]
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 */
object ConfigurationFactory {

    private var mongoDbConfig = json {
        obj(
            "db_name" to "cyface",
            "connection_string" to "mongodb://localhost:27019",
            "data_source_name" to "cyface",
        )
    }

    private var measurementLimit: Long = 104_857_600L

    private var port = 8080

    private var authConfig = json { obj("type" to "local") }

    /**
     * The configuration for setting up the Mongo Database configuration.
     * All possible properties are described as part of the
     * [Vert.x Mongo documentation](https://vertx.io/docs/vertx-mongo-client/java/#_configuring_the_client).
     */
    fun mongoDbConfig(config: JsonObject): ConfigurationFactory {
        mongoDbConfig = config
        return this
    }

    /**
     * The default limit for the size of a measurement upload in bytes.
     */
    fun measurementLimit(limit: Long): ConfigurationFactory {
        measurementLimit = limit
        return this
    }

    /**
     * The default Port to use during testing.
     */
    fun port(port: Int): ConfigurationFactory {
        this.port = port
        return this
    }

    /**
     * Configuration for the [io.vertx.ext.web.handler.AuthenticationHandler] to use.
     */
    fun authConfig(config: JsonObject): ConfigurationFactory {
        authConfig = config
        return this
    }

    /**
     * Provide a mocked test fixture configuration.
     */
    fun mockedConfiguration(): Configuration {
        val ret = mock<Configuration> {
            on { mongoDb } doReturn mongoDbConfig
            on { httpPort } doReturn port
            on { uploadExpiration } doReturn 60_000L
            on { metricsEnabled } doReturn false
            on { storageTypeJson } doReturn json { obj("type" to "local") }
            on { measurementPayloadLimit } doReturn measurementLimit
            on { authConfig } doReturn authConfig
            on { httpPath } doReturn "/"
        }
        return ret
    }
}
