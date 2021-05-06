/*
 * Copyright (C) Cyface GmbH - All Rights Reserved
 *
 * This file is part of the Cyface Server Backend.
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package de.cyface.model;

import static de.cyface.model.Modality.BICYCLE;
import static de.cyface.model.Modality.UNKNOWN;
import static de.cyface.model.Modality.WALKING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests for the functionality provided directly by the {@link Measurement} class.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 1.0.0
 */
public class MeasurementTest {

    /**
     * A globally unique identifier of the simulated upload device. The actual value does not really matter.
     */
    private static final String DEVICE_IDENTIFIER = UUID.randomUUID().toString();
    /**
     * The measurement identifier used for the test measurement. The actual value does not matter that much. It
     * simulates a device wide unique identifier.
     */
    private static final long MEASUREMENT_IDENTIFIER = 1L;
    /**
     * The name of the user to add test data for.
     */
    private static final String TEST_USER_USERNAME = "guest";

    /**
     * Tests that writing the CSV header produces the correct output.
     */
    @Test
    void testWriteCsvHeaderRow() {
        // Arrange
        final var expectedHeader = "username,deviceId,measurementId,trackId,timestamp [ms],latitude,longitude,"
                + "speed [m/s],accuracy [m],modalityType,modalityTypeDistance [km],distance [km],modalityTypeTravelTime"
                + " [ms],travelTime [ms]";

        // Act
        // Assert
        Measurement.csvHeader(row -> assertThat(row, is(equalTo(expectedHeader))));
    }

    /**
     * Tests that a {@link Measurement} without any modality also works. The initial Modality was in this test case
     * deleted by the user
     */
    @Test
    void testWriteLocationAsCsvRows_withoutModalityChanges() {
        // Arrange
        final var point3DS = new ArrayList<Point3D>();
        point3DS.add(new Point3D(1.0f, -2.0f, 3.0f, 1_000L));
        final var metaData = metaData();
        final var identifier = metaData.getIdentifier();
        final var tracks = Arrays.asList(
                new Track(
                        Collections.singletonList(new RawRecord(identifier, 1_000L, latitude(1), longitude(1), null,
                                accuracy(1), speed(1), UNKNOWN)),
                        point3DS, point3DS, point3DS),
                new Track(
                        Collections.singletonList(new RawRecord(identifier, 3_000L, latitude(3), longitude(3), null,
                                accuracy(3), speed(3), UNKNOWN)),
                        point3DS, point3DS, point3DS));
        final var measurement = new Measurement(metaData, tracks);
        final var expectedOutput = "username,deviceId,measurementId,trackId,timestamp [ms],latitude,longitude,speed [m/s],accuracy [m],modalityType,modalityTypeDistance [km],distance [km],modalityTypeTravelTime [ms],travelTime [ms]\r\n"
                +
                TEST_USER_USERNAME + "," + DEVICE_IDENTIFIER + "," + MEASUREMENT_IDENTIFIER + ",1,1000," + latitude(1)
                + ","
                + longitude(1) + "," + speed(1) + "," + accuracy(1) + "," + UNKNOWN.getDatabaseIdentifier()
                + ",0.0,0.0,0,0\r\n" +
                TEST_USER_USERNAME + "," + DEVICE_IDENTIFIER + "," + MEASUREMENT_IDENTIFIER + ",2,3000," + latitude(3)
                + ","
                + longitude(3) + "," + speed(3) + "," + accuracy(3) + "," + UNKNOWN.getDatabaseIdentifier()
                + ",0.0,0.0,0,0\r\n";

        // Act
        final var csvOutput = new StringBuilder();
        measurement.asCsv(true, line -> {
            csvOutput.append(line).append("\r\n");
        });

        // Assert
        assertThat(csvOutput.toString(), is(equalTo(expectedOutput)));
    }

    /**
     * Tests that modality type changes are correctly handled.
     */
    @Test
    void testWriteLocationAsCsvRows_withModalityTypeChanges() {
        // Arrange
        final var point3DS = new ArrayList<Point3D>();
        point3DS.add(new Point3D(1.0f, -2.0f, 3.0f, 1_000L));
        final var metaData = metaData();
        final var identifier = metaData.getIdentifier();
        final var tracks = Arrays.asList(
                new Track(
                        Arrays.asList(
                                new RawRecord(identifier, 1_000L, latitude(1), longitude(1), null, accuracy(1),
                                        speed(1), WALKING),
                                new RawRecord(identifier, 1_500L, latitude(2), longitude(2), null, accuracy(2),
                                        speed(2), WALKING)),
                        point3DS, point3DS, point3DS),
                new Track(
                        Arrays.asList(
                                new RawRecord(identifier, 3_000L, latitude(3), longitude(3), null, accuracy(3),
                                        speed(3), BICYCLE),
                                new RawRecord(identifier, 4_000L, latitude(4), longitude(4), null, accuracy(4),
                                        speed(4), BICYCLE)),
                        point3DS, point3DS, point3DS));
        final var measurement = new Measurement(metaData, tracks);

        final var csvOutput = new StringBuilder();
        final var expectedOutput = "username,deviceId,measurementId,trackId,timestamp [ms],latitude,longitude,speed [m/s],accuracy [m],modalityType,modalityTypeDistance [km],distance [km],modalityTypeTravelTime [ms],travelTime [ms]\r\n"
                +
                TEST_USER_USERNAME + "," + DEVICE_IDENTIFIER + "," + MEASUREMENT_IDENTIFIER + ",1,1000," + latitude(1)
                + ","
                + longitude(1) + "," + speed(1) + "," + accuracy(1) + "," + WALKING.getDatabaseIdentifier()
                + ",0.0,0.0,0,0\r\n" +
                TEST_USER_USERNAME + "," + DEVICE_IDENTIFIER + "," + MEASUREMENT_IDENTIFIER + ",1,1500," + latitude(2)
                + ","
                + longitude(2) + "," + speed(2) + "," + accuracy(2) + "," + WALKING.getDatabaseIdentifier()
                + ",13.12610864737932,13.12610864737932,500,500\r\n" +
                TEST_USER_USERNAME + "," + DEVICE_IDENTIFIER + "," + MEASUREMENT_IDENTIFIER + ",2,3000," + latitude(3)
                + ","
                + longitude(3) + "," + speed(3) + "," + accuracy(3) + "," + BICYCLE.getDatabaseIdentifier()
                + ",0.0,13.12610864737932,0,500\r\n" +
                TEST_USER_USERNAME + "," + DEVICE_IDENTIFIER + "," + MEASUREMENT_IDENTIFIER + ",2,4000," + latitude(4)
                + ","
                + longitude(4) + "," + speed(4) + "," + accuracy(4) + "," + BICYCLE.getDatabaseIdentifier()
                + ",13.110048189675535,26.236156837054857,1000,1500\r\n";

        // Act
        measurement.asCsv(true, line -> {
            csvOutput.append(line).append("\r\n");
        });
        // oocut.writeLocationsAsCsvRows(response, measurement);

        // Assert
        assertThat(csvOutput.toString(), is(equalTo(expectedOutput)));
    }

    @Test
    void testWriteMeasurementAsGeoJson() {
        // Arrange
        final var point3DS = new ArrayList<Point3D>();
        point3DS.add(new Point3D(1.0f, -2.0f, 3.0f, 1_000L));
        final var metaData = metaData();
        final var identifier = metaData.getIdentifier();
        final var tracks = Arrays.asList(
                new Track(
                        Arrays.asList(
                                new RawRecord(identifier, 1_000L, latitude(1), longitude(1), null, accuracy(1),
                                        speed(1), UNKNOWN),
                                new RawRecord(identifier, 2_000L, latitude(2), longitude(2), null, accuracy(2),
                                        speed(2), UNKNOWN)),
                        point3DS, point3DS, point3DS),
                new Track(
                        Collections.singletonList(new RawRecord(identifier, 3_000L, latitude(3), longitude(3), null,
                                accuracy(3), speed(3), UNKNOWN)),
                        point3DS, point3DS, point3DS));
        final var measurement = new Measurement(metaData, tracks);
        final var expectedOutput = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"MultiLineString\",\"coordinates\":"
                + "[[[13.1,51.1],[13.2,51.2]],[[13.3,51.3]]]},\"properties\":{\"deviceId\":\""
                + identifier.getDeviceIdentifier() + "\"," + "\"measurementId\":"
                + identifier.getMeasurementIdentifier() + ",\"length\":0.0}}";

        // Act
        final var jsonOutput = new StringBuilder();
        measurement.asGeoJson(jsonOutput::append);

        // Assert
        assertThat(jsonOutput.toString(), is(equalTo(expectedOutput)));
    }

    @Test
    void testWriteMeasurementAsJson() {
        // Arrange
        final var point3DS = new ArrayList<Point3D>();
        point3DS.add(new Point3D(1.0f, -2.0f, 3.0f, 1_000L));
        final var metaData = metaData();
        final var identifier = metaData.getIdentifier();
        final var tracks = Collections.singletonList(
                new Track(Collections.singletonList(
                        new RawRecord(identifier, 1_000L, latitude(1), longitude(1), null, accuracy(1),
                                speed(1), UNKNOWN)),
                        point3DS, point3DS, point3DS));
        final var measurement = new Measurement(metaData, tracks);
        final var expectedOutput = "{\"metaData\":{\"username\":\"guest\",\"deviceId\":\""
                + identifier.getDeviceIdentifier() + "\",\"measurementId\":" + identifier.getMeasurementIdentifier()
                + ",\"length\":0.0},\"tracks\":[{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\","
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[13.1,51.1]},\"properties\":{\"timestamp\":1000,"
                + "\"speed\":0.1,\"accuracy\":10.0,\"modality\":\"UNKNOWN\"}}]}]}";

        // Act
        final var jsonOutput = new StringBuilder();
        measurement.asJson(jsonOutput::append);

        // Assert
        assertThat(jsonOutput.toString(), is(equalTo(expectedOutput)));
    }

    /**
     * @param index The 1-based index of the latitude to generate
     * @return A valid latitude value (although it might semantically make no sense)
     */
    private double latitude(final int index) {
        return 51.0 + index / 10.0;
    }

    /**
     * @param index The 1-based index of the longitude to generate
     * @return A valid longitude value (although it might semantically make no sense)
     */
    private double longitude(final int index) {
        return 13.0 + index / 10.0;
    }

    /**
     * @param index The 1-based index of the speed to generate
     * @return A valid speed value (although it might semantically make no sense)
     */
    private double speed(final int index) {
        return 0.0 + index * 0.1;
    }

    /**
     * @param index The 1-based index of the accuracy to generate
     * @return A valid accuracy value (although it might semantically make no sense)
     */
    private double accuracy(final int index) {
        return 0.0 + index * 10.0;
    }

    /**
     * @return A static set of meta data to be used by test {@link Measurement} instances
     */
    private MetaData metaData() {
        return new MetaData(new MeasurementIdentifier(DEVICE_IDENTIFIER, MEASUREMENT_IDENTIFIER),
                "Android SDK built for x86", "Android 8.0.0",
                "2.7.0-beta1", 0.0, TEST_USER_USERNAME, "1.0.0");
    }
}
