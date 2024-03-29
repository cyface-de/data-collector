/*
 * Copyright 2022-2023 Cyface GmbH
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

import de.cyface.collector.configuration.AuthType
import de.cyface.collector.configuration.Configuration
import de.cyface.collector.configuration.GridFsStorageType
import io.vertx.core.json.JsonObject
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URL
import java.nio.file.Path

/**
 * A factory for the creation of a test fixture [Configuration]
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.2.0
 */
object ConfigurationFactory {
    /**
     * Provide a mocked test fixture configuration.
     */
    fun mockedConfiguration(port: Int, mongoDbConfig: JsonObject, measurementLimit: Long?): Configuration {
        val ret = mock<Configuration> {
            on { mongoDb } doReturn mongoDbConfig
            on { serviceHttpAddress } doReturn
                URL(
                    "https",
                    "localhost",
                    port,
                    "/api/v4/*"
                )
            on { uploadExpiration } doReturn 60_000L
            URL(
                "https",
                "localhost",
                port,
                "/"
            )
            on { metricsEnabled } doReturn false
            on { storageType } doReturn GridFsStorageType(Path.of("uploadFolder"))
            if (measurementLimit != null) {
                on { measurementPayloadLimit } doReturn measurementLimit
            } else {
                on { measurementPayloadLimit } doReturn 104_857_600L
            }
            on { authType } doReturn AuthType.Mocked
            on { oauthCallback } doReturn URL("http://localhost:8080/callback")
            on { oauthClient } doReturn "collector-test"
            on { oauthSecret } doReturn "SECRET"
            on { oauthSite } doReturn URL("https://example.com:8443/realms/{tenant}")
            on { oauthTenant } doReturn "rfr"
        }
        return ret
    }
}
