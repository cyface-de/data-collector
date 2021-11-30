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
package de.cyface.api;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.Validate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cyface.api.model.TrackBucket;
import de.cyface.model.Measurement;
import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.MetaData;
import de.cyface.model.Modality;
import de.cyface.model.RawRecord;
import de.cyface.model.Track;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public class MeasurementIteratorTest {

    final ReadStream<JsonObject> mockedSource = new ReadStream<>() {
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private Handler<Throwable> exceptionHandler;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private Handler<JsonObject> handler;
        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private Handler<Void> endHandler;

        @Override
        public ReadStream<JsonObject> exceptionHandler(Handler<Throwable> handler) {
            this.exceptionHandler = handler;
            return this;
        }

        @Override
        public ReadStream<JsonObject> handler(Handler<JsonObject> handler) {
            this.handler = handler;
            return this;
        }

        @Override
        public ReadStream<JsonObject> endHandler(Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;
        }

        @Override
        public ReadStream<JsonObject> pause() {
            // Not yet implemented as currently not needed in this test
            return this;
        }

        @Override
        public ReadStream<JsonObject> resume() {
            // Not yet implemented as currently not needed in this test
            return this;
        }

        @Override
        public ReadStream<JsonObject> fetch(long amount) {
            // Not yet implemented as currently not needed in this test
            return this;
        }
    };

    /**
     * Ensures track buckets are sorted before they are composed to a Track.
     * /
    @Test
    void testTracks_toSortBuckets() {

        // Arrange
        final var strategy = new MeasurementRetrievalWithoutSensorData();
        final var trackBuckets = generateLocationDocs(1, 0, 2, Modality.BICYCLE);
        final var locations = trackBuckets.stream().map(location -> {
            final var measurementId = strategy.metaData(location).getIdentifier();
            return strategy.geoLocation(location, measurementId);
        }).collect(Collectors.toList());
        final Promise<MeasurementIterator> promise = Promise.promise();
        new MeasurementIterator(mockedSource, strategy, promise::fail, oocut -> {

            // Un-sort track buckets
            locations.sort(Comparator.comparing(TrackBucket::getBucket).reversed());

            // Act
            final var tracks = oocut.tracks(locations);

            // Assert
            final var expectedTrack = generateMeasurement(1, 1, new Modality[] {Modality.BICYCLE}).getTracks().get(0);
            assertThat(tracks.size(), is(equalTo(1)));
            assertThat(tracks.get(0), is(equalTo(expectedTrack)));
        });
    }*/

    /**
     * Ensures tracks are sorted before they are composed to a Track list.
     * /
    @Test
    void testTracks_toSortTracks() {
        // Arrange
        final var strategy = new MeasurementRetrievalWithoutSensorData();
        final var trackBuckets = generateLocationDocs(1, 1, 1, Modality.BICYCLE);
        trackBuckets.addAll(generateLocationDocs(1, 0, 1, Modality.WALKING));
        final var buckets = trackBuckets.stream().map(location -> {
            final var measurementId = strategy.metaData(location).getIdentifier();
            return strategy.geoLocation(location, measurementId);
        }).collect(Collectors.toList());
        final Promise<MeasurementIterator> promise = Promise.promise();
        new MeasurementIterator(mockedSource, strategy, promise::fail, oocut -> {

            // Act
            final var tracks = oocut.tracks(buckets);

            // Assert
            final var expectedTracks = generateMeasurement(1, 2, new Modality[] {Modality.WALKING, Modality.BICYCLE})
                    .getTracks();
            assertThat(tracks, is(equalTo(expectedTracks)));
        });
    }*/

    /**
     * Ensures data in the current database format ("track buckets") can be converted to {@code Measurement}s.
     */
    @ParameterizedTest
    @MethodSource("provideDocumentsForMeasurements")
    void testMeasurement(final List<JsonObject> documents, final Measurement expectedMeasurement) {
        // Arrange
        final var strategy = new MeasurementRetrievalWithoutSensorData();
        final var metaData = strategy.metaData(documents.get(0));
        final var locationDocuments = documents.stream().filter(d -> d.getJsonObject("metaData").getString("sensorType").equals("location")).collect(Collectors.toList());
        Validate.isTrue(!locationDocuments.isEmpty());
        final var locations = new HashMap<Integer, List<RawRecord>>();
        locationDocuments.forEach(document -> {
            final var trackId = strategy.trackId(document);
            if (!locations.containsKey(trackId)) {
                locations.put(trackId, new ArrayList<>());
            }
            final var measurementId = strategy.metaData(document).getIdentifier();
            locations.get(trackId).add(strategy.geoLocation(document, measurementId));
        });

        final Promise<MeasurementIterator> promise = Promise.promise();
        new MeasurementIterator(mockedSource, strategy, promise::fail, oocut -> {

            // Act
            final var measurement = oocut.measurement(locations, metaData);

            // Assert
            assertThat(measurement, is(equalTo(expectedMeasurement)));
        });
    }

    private static Stream<Arguments> provideDocumentsForMeasurements() {
        // FIXME: test cases -> adjust to new structure

        // Small test case
        final var singleMeasurementDocs = generateLocationDocs(1, 0, Modality.BICYCLE);
        final var singleMeasurement = generateMeasurement(1, 1, new Modality[] {Modality.BICYCLE});
/*
        // Multiple tracks in one measurement
        final var multipleTracksDocs = generateLocationDocs(1, 0, 1, Modality.BICYCLE);
        multipleTracksDocs.addAll(generateLocationDocs(1, 1, 1, Modality.BICYCLE));
        final var multipleTracksMeasurement = generateMeasurement(1, 2, new Modality[] {Modality.BICYCLE});

        // Multiple buckets in one track
        final var multipleBucketsDocs = generateLocationDocs(1, 0, 2, Modality.BICYCLE);
        final var multipleBucketsMeasurement = generateMeasurement(1, 1, new Modality[] {Modality.BICYCLE});*/

        return Stream.of(
                Arguments.of(singleMeasurementDocs, singleMeasurement)/*,
                Arguments.of(multipleTracksDocs, multipleTracksMeasurement),
                Arguments.of(multipleBucketsDocs, multipleBucketsMeasurement)*/);
    }

    private static List<JsonObject> generateLocationDocs(
            @SuppressWarnings("SameParameterValue") final int measurementId, final int trackId, final Modality modality) {

        final var locationsValues = new ArrayList<JsonObject>();
        locationsValues.add(new JsonObject(
                "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[13.772176666666667,51.075295000000004]},\"elevation\":null,\"speed\":13.039999961853027,\"accuracy\":27.04,\"modality\":\""
                        + modality + "\"}"));
        locationsValues.add(new JsonObject(
                "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[13.77215,51.0753]},\"elevation\":null,\"speed\":13.039999961853027,\"accuracy\":16.85,\"modality\":\""
                        + modality + "\"}"));
        locationsValues.add(new JsonObject(
                "{\"geometry\":{\"type\":\"Point\",\"coordinates\":[13.77215,51.0753]},\"elevation\":null,\"speed\":13.039999961853027,\"accuracy\":27.25,\"modality\":\""
                        + modality + "\"}"));

        final var documents = new ArrayList<JsonObject>();
        for (var i = 0; i < locationsValues.size(); i++) {
            final var document = new JsonObject();
            document.put("_id", new JsonObject().put("$oid", "5fe20d2606d9464d0fa92dba"));
            document.put("metaData",
                    new JsonObject().put("deviceId", "testDiD").put("measurementId", measurementId)
                            .put("deviceType", "testType")
                            .put("osVersion", "Android 10").put("appVersion", "0.0.0").put("length", 28.34324)
                            .put("username", "guest").put("version", "2.0.0")
                            .put("trackId", trackId).put("sensorType", "location"));
            document.put("timestamp", Instant.ofEpochMilli(1608650009000L + i*1000L));
            document.put("value", locationsValues.get(i));
            documents.add(document);
        }
        return documents;
    }

    private static Measurement generateMeasurement(@SuppressWarnings("SameParameterValue") final int measurementId,
            final int numberOfTracks, final Modality[] modalities) {
        Validate.isTrue(modalities.length <= 2, "Not implemented");

        final var measurementIdentifier = new MeasurementIdentifier("testDiD", measurementId);
        final var expectedMetaData = new MetaData(measurementIdentifier, "testType", "Android 10",
                "0.0.0", 28.34324, "guest", "2.0.0");
        final var expectedTracks = new ArrayList<Track>();
        for (int i = 0; i < numberOfTracks; i++) {
            final var modality = modalities.length == 1 ? modalities[0] : i == 0 ? modalities[0] : modalities[1];
            final var expectedLocations = new ArrayList<RawRecord>();
            expectedLocations.add(new RawRecord(measurementIdentifier, 1608650009000L,
                    51.075295000000004, 13.772176666666667, 27.04, 13.039999961853027, modality));
            expectedLocations.add(new RawRecord(measurementIdentifier, 1608650010000L,
                    51.0753, 13.77215, 16.85, 13.039999961853027, modality));
            expectedLocations.add(new RawRecord(measurementIdentifier, 1608650010000L,
                    51.0753, 13.77215, 27.25, 13.039999961853027, modality));
            expectedTracks.add(new Track(expectedLocations, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }
        return new Measurement(expectedMetaData, expectedTracks);
    }
}
