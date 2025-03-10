package de.cyface.collector.auth

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import de.cyface.collector.commons.DataCollectorApi
import de.cyface.collector.commons.MongoTest
import de.cyface.collector.model.MeasurementIdentifier
import de.cyface.collector.model.metadata.ApplicationMetaData
import de.cyface.collector.model.metadata.DeviceMetaData
import de.cyface.collector.model.metadata.GeoLocation
import de.cyface.collector.model.metadata.MeasurementMetaData
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import kotlin.test.Test

/**
 * Tests the JWK authentication against the Cyface Data Collector.
 *
 * To run this test one needs a JWK output and a valid token.
 * The way to get this information is documented in Confluence.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(VertxExtension::class)
@Disabled(
    """This test requires a valid token and JWK output to run. 
    |Since this is sensitive information, it is disabled by default."""
)
class JWKAuthIT {

    @Test
    fun test(vertx: Vertx) = runTest {
        // Arrange
        // The following to parameters must be set, before running this test.
        val authToken = "" // See Confluence for how to get this
        val jwkJson = json {
            obj(
                // Add JWK here. See Confluence for how to get this. The format should look like this:
                // "kty" to "***",
                // ...
            )
        }

        val mongoTest = MongoTest()
        mongoTest.setUpMongoDatabase()
        val api = DataCollectorApi(140_000, vertx)
        api.runCollectorApi(vertx, mongoTest, JWKAuthHandlerBuilder(vertx, jwkJson))

        // Act
        val response = api.preRequest(
            authToken = authToken,
            measurementIdentifier = MeasurementIdentifier(UUID.randomUUID(), 1L),
            measurementMetaData = MeasurementMetaData(
                length = 0.0,
                locationCount = 2,
                startLocation = GeoLocation(1740408585942L, 13.710343496364144, 51.04970403951547),
                endLocation = GeoLocation(1740406200000L, 13.706982164108865, 51.03318545551225),
                modality = "BICYCLE"
            ),
            deviceMetaData = DeviceMetaData(
                deviceType = "iPhone",
                operatingSystemVersion = "14.0",
            ),
            applicationMetaData = ApplicationMetaData(
                applicationVersion = "1.0",
                formatVersion = 3,
            ),
        )

        // Assert
        assertThat(response.statusCode(), equalTo(200))
    }
}
