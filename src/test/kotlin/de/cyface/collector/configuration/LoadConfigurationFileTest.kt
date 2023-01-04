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
package de.cyface.collector.configuration

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A test for parsing Vertx configuration files.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
class LoadConfigurationFileTest {

    /**
     * The happy path test for loading a Vertx configuration file.
     */
    @Test
    fun `Loading a Configuration File Happy Path`() {
        // Arrange
        val countDownLatch = CountDownLatch(1)

        // Act
        val jsonConfiguration = loadConf("test-conf.json")
        val decodedConfiguration = Configuration.deserialize(jsonConfiguration)
        decodedConfiguration.onComplete { countDownLatch.countDown() }

        // Assert
        countDownLatch.await(1, TimeUnit.SECONDS)
        val result = decodedConfiguration.result()
        assertThat(result.adminPassword, equalTo("secret"))
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
    fun `Unkown additional Parameters should be ignored`() {
        // Arrange
        val jsonConfiguration = loadConf("test-conf-with-additional-parameter.json")
        val countDownLatch = CountDownLatch(1)

        // Act
        val decodedConfigurationCall = Configuration.deserialize(jsonConfiguration)

        // Assert
        decodedConfigurationCall.onComplete { countDownLatch.countDown() }
        countDownLatch.await(1L, TimeUnit.SECONDS)
        assertFalse(decodedConfigurationCall.failed())
        assertTrue(decodedConfigurationCall.succeeded())
    }

    /**
     * Ensure that a config that contains the salt and the salt.path configuration key,
     * throws an exception. These two should be mutual exclusive.
     */
    @Test
    fun `Config contains salt and salt path which should throw an Exception`() {
        // Arrange
        val jsonConfiguration = loadConf("test-conf-with-salt-and-saltpath.json")

        // Act / Assert
        assertThrows<InvalidConfig> {
            Configuration.deserialize(jsonConfiguration)
        }
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
