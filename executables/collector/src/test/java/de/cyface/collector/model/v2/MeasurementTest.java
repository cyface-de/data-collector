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
package de.cyface.collector.model.v2;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

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
        final var uploads = new HashSet<Measurement.FileUpload>();
        final var measurementFile = Paths.get("testDir", "measurementFile").toAbsolutePath().toFile();
        final var eventFile = Paths.get("testDir", "eventFile").toAbsolutePath().toFile();
        //noinspection SpellCheckingInspection
        uploads.add(new Measurement.FileUpload(measurementFile, "ccyf"));
        //noinspection SpellCheckingInspection
        uploads.add(new Measurement.FileUpload(eventFile, "ccyfe"));
        final var measurement = new Measurement(did, mid, osVersion, deviceType, appVersion, length, locationCount, startLocation, endLocation, vehicle, username, uploads);

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

        final var decodedUploads = decoded.getFileUploads();
        //noinspection SpellCheckingInspection
        final var decodedMeasurementFiles = decodedUploads.stream().filter(f -> f.getFileType().equals("ccyf")).collect(Collectors.toList());
        //noinspection SpellCheckingInspection
        final var decodedEventFiles = decodedUploads.stream().filter(f -> f.getFileType().equals("ccyfe")).collect(Collectors.toList());
        assertThat(decodedMeasurementFiles.size(), is(equalTo(1)));
        assertThat(decodedEventFiles.size(), is(equalTo(1)));
        final var decodedMeasurementFile = decodedMeasurementFiles.get(0);
        final var decodedEventFile = decodedEventFiles.get(0);
        assertThat(decodedMeasurementFile.getFile(), is(equalTo(measurementFile)));
        //noinspection SpellCheckingInspection
        assertThat(decodedMeasurementFile.getFileType(), is(equalTo("ccyf")));
        assertThat(decodedEventFile.getFile(), is(equalTo(eventFile)));
        //noinspection SpellCheckingInspection
        assertThat(decodedEventFile.getFileType(), is(equalTo("ccyfe")));
    }
}
