package de.cyface.collector.commons

import de.cyface.collector.configuration.Configuration
import de.cyface.collector.configuration.GridFsStorageType
import de.cyface.collector.configuration.ValueSalt
import io.vertx.core.json.JsonObject
import org.apache.commons.lang3.Validate
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URL
import java.nio.file.Path

object ConfigurationFactory {
    fun mockedConfiguration(port: Int, mongoDbConfig: JsonObject, measurementLimit: Long?): Configuration {
        val privateKey = this.javaClass.getResource("/private_key.pem")
        val publicKey = this.javaClass.getResource("/public.pem")
        Validate.notNull(privateKey)
        Validate.notNull(publicKey)
        val privateKeyUri = privateKey!!.toURI()
        val publicKeyUri = publicKey!!.toURI()
        val ret = mock<Configuration> {
            on { jwtPrivate } doReturn Path.of(privateKeyUri)
            on { jwtPublic } doReturn Path.of(publicKeyUri)
            on { mongoDb } doReturn mongoDbConfig
            on { serviceHttpAddress } doReturn
                URL(
                    "https",
                    "localhost",
                    port,
                    "/api/v3/*"
                )
            on { adminUser } doReturn "admin"
            on { adminPassword } doReturn "secret"
            on { salt } doReturn ValueSalt("cyface-salt")
            on { jwtExpiration } doReturn 10_000
            on { uploadExpiration } doReturn 60_000L
            on { managementHttpAddress } doReturn
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
                on { measurementPayloadLimit } doReturn 10_000L
            }
        }
        return ret
    }
}
