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

class LoadConfigurationFileTest {

    @Test
    fun test() {
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

    // missing parameter
    @Test
    fun `Parsing Configuration with Missing Parameter, should throw an Exception`() {
        // Arrange
        val jsonConfiguration = loadConf("test-conf-missing.json")

        // Act
        assertThrows<InvalidConfig> {
            Configuration.deserialize(jsonConfiguration)
        }
    }

    // wrongly spelled or to many parameters
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

    // salt and salt path
    @Test
    fun `Config contains salt and salt path which should throw an Exception`() {
        // Arrange
        val jsonConfiguration = loadConf("test-conf-with-salt-and-saltpath.json")

        // Act / Assert
        assertThrows<InvalidConfig> {
            Configuration.deserialize(jsonConfiguration)
        }
    }

    private fun loadConf(resourceName: String): JsonObject {
        val resource = this::class.java.getResource("/configurations/$resourceName")
        assertNotNull(resource)
        val encodedConfiguration = Files.readString(Path.of(resource.toURI()))
        return JsonObject(encodedConfiguration)
    }
}
