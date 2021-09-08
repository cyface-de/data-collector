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

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
        final var startLocation = new GeoLocation(51.1, 13.1, 1000);
        final var endLocation = new GeoLocation(51.2, 13.2, 2000);
        final var vehicle = "CAR";
        final var username = "guest";
        final var binary = Paths.get("testDir", "testFile").toAbsolutePath().toFile();
        final var formatVersion = 2;
        final var measurement = new Measurement(did, mid, osVersion, deviceType, appVersion, length, locationCount, startLocation, endLocation, vehicle, username, binary, formatVersion);

        // Act: encode
        final var codec = new Measurement.EventBusCodec();
        final var buffer = Buffer.buffer();
        codec.encodeToWire(buffer, measurement);

        // Act: decode
        final var decoded = codec.decodeFromWire(0, buffer);

        // Assert
        assertThat(decoded.getDeviceIdentifier(), is(equalTo(did)));
        assertThat(decoded.getMeasurementIdentifier(), is(equalTo(mid)));
        assertThat(decoded.getOperatingSystemVersion(), is(equalTo(osVersion)));
        assertThat(decoded.getDeviceType(), is(equalTo(deviceType)));
        assertThat(decoded.getApplicationVersion(), is(equalTo(appVersion)));
        assertThat(decoded.getLength(), is(equalTo(length)));
        assertThat(decoded.getLocationCount(), is(equalTo(locationCount)));

        final var decodedStart = decoded.getStartLocation();
        final var decodedEnd = decoded.getEndLocation();
        assertThat(decodedStart.getLat(), is(equalTo(startLocation.getLat())));
        assertThat(decodedStart.getLon(), is(equalTo(startLocation.getLon())));
        assertThat(decodedStart.getTimestamp(), is(equalTo(startLocation.getTimestamp())));
        assertThat(decodedEnd.getLat(), is(equalTo(endLocation.getLat())));
        assertThat(decodedEnd.getLon(), is(equalTo(endLocation.getLon())));
        assertThat(decodedEnd.getTimestamp(), is(equalTo(endLocation.getTimestamp())));

        assertThat(decoded.getVehicle(), is(equalTo(vehicle)));
        assertThat(decoded.getUsername(), is(equalTo(username)));
        assertThat(decoded.getBinary(), is(equalTo(binary)));
        assertThat(decoded.getFormatVersion(), is(equalTo(formatVersion)));
    }
}
