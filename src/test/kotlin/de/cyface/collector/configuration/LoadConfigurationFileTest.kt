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
package de.cyface.collector.configuration

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNotNull

/**
 * A test for parsing Vertx configuration files.
 *
 * @author Klemens Muthmann
 * @version 1.1.0
 */
class LoadConfigurationFileTest {

    /**
     * The happy path test for loading a Vertx configuration file.
     */
    @Test
    fun `Loading a Configuration File Happy Path`() {
        // Arrange

        // Act
        val jsonConfiguration = loadConf("test-conf.json")
        val result = Configuration.deserialize(jsonConfiguration)

        // Assert
        assertThat(result.storageType, isA<GoogleCloudStorageType>())
    }

    /**
     * Ensures that loading a configuration with a missing parameter throws the appropriate [Exception].
     */
    @Test
    fun `Parsing Configuration with Missing Parameter, should throw an Exception`() {
        // Arrange
        val jsonConfiguration = loadConf("test-conf-missing.json")

        // Act
        assertThrows<InvalidConfig> {
            Configuration.deserialize(jsonConfiguration)
        }
    }

    /**
     * Ensure that wrong or wrongly spelled parameters are ignored as long as the Configuration is still
     * complete.
     */
    @Test
    fun `Unknown additional Parameters should be ignored`() {
        // Arrange
        val jsonConfiguration = loadConf("test-conf-with-additional-parameter.json")

        // Act
        val result = Configuration.deserialize(jsonConfiguration)

        // Assert
        assertNotNull(result)
    }

    /**
     * Load a configuration from a resource file.
     */
    private fun loadConf(resourceName: String): JsonObject {
        val resource = this::class.java.getResource("/configurations/$resourceName")
        assertNotNull(resource)
        val encodedConfiguration = Files.readString(Path.of(resource.toURI()))
        return JsonObject(encodedConfiguration)
    }
}
