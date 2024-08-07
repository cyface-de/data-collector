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
package de.cyface.collector.model

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import de.cyface.collector.model.metadata.MeasurementMetaData

/**
 * Test the creation of [MeasurementMetaData] objects.
 *
 * @author Klemens Muthmann
 */
class MetaDataFactoryTest {

    /**
     * Test that the happy path for creating measurement meta data works as expected with some example data.
     */
    @Test
    fun `Creating measurement meta data works as expected from valid json input`() {
        // Arrange
        val json = JsonObject()

        json.put(FormAttributes.LENGTH.value, 1.0)
        json.put(FormAttributes.LOCATION_COUNT.value, 10L)
        json.put(FormAttributes.START_LOCATION_LON.value, 51.047774)
        json.put(FormAttributes.START_LOCATION_LAT.value, 13.713167)
        json.put(FormAttributes.START_LOCATION_TS.value, 1723024244123L)
        json.put(FormAttributes.END_LOCATION_LAT.value, 13.718692)
        json.put(FormAttributes.END_LOCATION_LON.value, 51.051045)
        json.put(FormAttributes.END_LOCATION_TS.value, 1723024336123L)
        json.put(FormAttributes.MODALITY.value, "BICYCLE")

        val oocut = object : MeasurementMetaDataFactory {

        }

        // Act
        val result = oocut.measurementMetaData(json)

        // Assert
        assertEquals(1.0, result.length)
        assertEquals(10L, result.locationCount)
        assertEquals(51.047774, result.startLocation?.longitude)
        assertEquals(13.713167, result.startLocation?.latitude)
        assertEquals(1723024244123L, result.startLocation?.timestamp)
        assertEquals(51.051045, result.endLocation?.longitude)
        assertEquals(13.718692, result.endLocation?.latitude)
        assertEquals(1723024336123L, result.endLocation?.timestamp)
        assertEquals("BICYCLE", result.modality)
    }

}
