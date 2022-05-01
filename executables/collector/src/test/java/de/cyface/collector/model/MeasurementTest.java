/*
 * Copyright 2021 Cyface GmbH
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
package de.cyface.collector.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.cyface.model.RequestMetaData;
import io.vertx.core.buffer.Buffer;

/**
 * Tests the inner workings of the {@link Measurement} class.
 *
 * @author Armin Schnabel
 * @version 1.0.0
 * @since 6.0.0
 */
public class MeasurementTest {

    @Test
    public void testEventBusCodec() {
        // Arrange
        final var did = UUID.randomUUID().toString();
        final var mid = "1";
        final var osVersion = "10.0.0";
        final var deviceType = "Emulator";
        final var appVersion = "1.2.3-beta1";
        final var length = 0.0;
        final var locationCount = 2L;
        final var startLocation = new RequestMetaData.GeoLocation(1000, 51.1, 13.1);
        final var endLocation = new RequestMetaData.GeoLocation(2000, 51.2, 13.2);
        final var modality = "CAR";
        final var userId = "624d8c51c0879068499676c6";
        final var binary = Paths.get("testDir", "testFile").toAbsolutePath().toFile();
        final var formatVersion = 2;
        final var metaData = new RequestMetaData(did, mid, osVersion, deviceType, appVersion, length, locationCount,
                startLocation, endLocation, modality, formatVersion);
        final var measurement = new Measurement(metaData, userId, binary);

        // Act: encode
        final var codec = new Measurement.EventBusCodec();
        final var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, measurement);

        // Act: decode
        final var decoded = codec.decodeFromWire(0, buffer);

        // Assert
        final var decodedMetaData = decoded.getMetaData();
        assertThat(decodedMetaData.getDeviceIdentifier(), is(equalTo(did)));
        assertThat(decodedMetaData.getMeasurementIdentifier(), is(equalTo(mid)));
        assertThat(decodedMetaData.getOperatingSystemVersion(), is(equalTo(osVersion)));
        assertThat(decodedMetaData.getDeviceType(), is(equalTo(deviceType)));
        assertThat(decodedMetaData.getApplicationVersion(), is(equalTo(appVersion)));
        assertThat(decodedMetaData.getLength(), is(equalTo(length)));
        assertThat(decodedMetaData.getLocationCount(), is(equalTo(locationCount)));

        final var decodedStart = decodedMetaData.getStartLocation();
        final var decodedEnd = decodedMetaData.getEndLocation();
        assertThat(decodedStart.getLatitude(), is(equalTo(startLocation.getLatitude())));
        assertThat(decodedStart.getLongitude(), is(equalTo(startLocation.getLongitude())));
        assertThat(decodedStart.getTimestamp(), is(equalTo(startLocation.getTimestamp())));
        assertThat(decodedEnd.getLatitude(), is(equalTo(endLocation.getLatitude())));
        assertThat(decodedEnd.getLongitude(), is(equalTo(endLocation.getLongitude())));
        assertThat(decodedEnd.getTimestamp(), is(equalTo(endLocation.getTimestamp())));

        assertThat(decodedMetaData.getModality(), is(equalTo(modality)));
        assertThat(decoded.getUserId(), is(equalTo(userId)));
        assertThat(decoded.getBinary(), is(equalTo(binary)));
        assertThat(decodedMetaData.getFormatVersion(), is(equalTo(formatVersion)));
    }
}
