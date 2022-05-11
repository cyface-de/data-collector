/*
 * Copyright 2021-2022 Cyface GmbH
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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;

import de.cyface.api.model.TrackBucket;
import de.cyface.model.Measurement;
import de.cyface.model.MeasurementIdentifier;
import de.cyface.model.Track;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

/**
 * This class represents a stream of {@code Measurement}s with lazy-load.
 * <p>
 * Use the method {@link #load(Handler, Handler, Handler, Handler)} to load all measurements and handle each at a time.
 *
 * @author Armin Schnabel
 * @since 6.0.0
 * @version 1.0.0
 */
final public class MeasurementIterator implements Iterator<Future<Measurement>>, Handler<JsonObject> {

    /**
     * The source where the measurement data is loaded from, usually a database.
     */
    private final ReadStream<JsonObject> bucketStream;
    /**
     * The {@code Handler} called upon errors.
     */
    private final Handler<Throwable> failureHandler;
    /**
     * The strategy to be used when loading measurement data.
     */
    private final MeasurementRetrievalStrategy strategy;
    /**
     * A {@code Handler} called when the instance of this class is fully initialized.
     */
    private final Handler<MeasurementIterator> initializedHandler;
    /**
     * Indicates weather this {@link MeasurementIterator} is initialized and returns meaningful responses.
     * <p>
     * This is necessary as we need to wait for the first bucket to be loaded before we can respond to `hasNext()`.
     */
    private boolean initialized;
    /**
     * The {@link TrackBucket}s already loaded but not yet returned, usually, because not all buckets of the current
     * measurement are loaded.
     */
    private final List<TrackBucket> cachedBuckets;
    /**
     * The {@link MeasurementIdentifier} of the measurement which is currently loaded from database.
     */
    private MeasurementIdentifier cachedMeasurementId;
    /**
     * The {@code Promise} of the last {@link #next()} request which initialized the loading of the measurement data for
     * {@link #cachedMeasurementId}.
     */
    private Promise<Measurement> nextMeasurement = null;
    /**
     * A {@code Semaphore} which ensures that only one {@link #next()} call is pending at a time. The caller has to wait
     * for the {@link #nextMeasurement} promise to be resolved before calling {@link #next()} again.
     */
    private final Semaphore pendingNextCall;
    /**
     * The handler to be called after the last bucket was loaded from the {@link #bucketStream} to construct and return
     * the last measurement to the caller.
     */
    private final LastMeasurementHandler lastMeasurementHandler;

    /**
     * Constructs an instance of this class.
     * <p>
     * This instance is initialized when {@code #initializedHandler} is called.
     *
     * @param bucketStream the stream to read measurement data from
     * @param strategy the strategy to be used when loading measurement data
     * @param failureHandler the {@code Handler} called upon errors
     * @param initializedHandler a {@code Handler} called when the instance of this class is fully initialized
     */
    public MeasurementIterator(final ReadStream<JsonObject> bucketStream, final MeasurementRetrievalStrategy strategy,
                               final Handler<Throwable> failureHandler, final Handler<MeasurementIterator> initializedHandler) {

        this.lastMeasurementHandler = new LastMeasurementHandler(this);
        bucketStream
                .pause() // only load data when requested via `next()`
                .handler(this)
                .exceptionHandler(failureHandler)
                .endHandler(lastMeasurementHandler);

        this.bucketStream = bucketStream;
        this.strategy = strategy;
        this.failureHandler = failureHandler;
        this.cachedBuckets = new ArrayList<>();
        this.initializedHandler = initializedHandler;
        this.pendingNextCall = new Semaphore(1);

        // Load the first bucket to be able to answer to `hasNext()` calls
        bucketStream.fetch(1);
    }

    @Override
    public void handle(final JsonObject document) {
        try {
            final var bucket = strategy.trackBucket(document);
            final var measurementId = bucket.getMetaData().getIdentifier();

            // Handle first bucket
            if (cachedMeasurementId == null) {
                cachedMeasurementId = measurementId;
                cachedBuckets.add(bucket);
                initialized = true;
                initializedHandler.handle(this);
                return;
            }

            // Handle subsequent buckets
            if (measurementId.equals(cachedMeasurementId)) {
                cachedBuckets.add(bucket);
                bucketStream.fetch(1);
                return;
            }

            // Handle bucket of next measurement (i.e. all buckets of previous measurement loaded)
            // System.out.println("MeasurementStream.handle(next measurements buckets) -> resolveNext");
            resolveNext(cachedBuckets, measurementId, bucket);
        } catch (ParseException e) {
            failureHandler.handle(e);
        }
    }

    /**
     * Loads the next measurement until there is no measurement on this {@link MeasurementIterator}.
     *
     * @param writeHandler The handler which is called when a measurement is loaded.
     * @param separationHandler The handler which is called between measurements, e.g. to add separators in the output
     * @param endHandler The handler which is called after the last measurement was loaded.
     * @param failureHandler The handler which is called upon errors.
     */
    public void load(final Handler<Measurement> writeHandler, final Handler<Void> separationHandler,
            final Handler<Void> endHandler, final Handler<Throwable> failureHandler) {

        if (!initialized) {
            // Seems like no entries where found for the requested data
            endHandler.handle(null);
        } else if (hasNext()) {
            final var future = next();
            future.onFailure(failureHandler)
                    .onSuccess(measurement -> {
                        writeHandler.handle(measurement);
                        if (hasNext()) {
                            separationHandler.handle(null);
                        }
                        load(writeHandler, separationHandler, endHandler, failureHandler);
                    });
        } else {
            lastMeasurementHandler.handle(null);
            endHandler.handle(null);
        }
    }

    @Override
    public boolean hasNext() {
        Validate.isTrue(initialized, "Calling hasNext() before initialized is not supported!");
        return cachedMeasurementId != null;
    }

    /**
     * @return Returns a {@code Promise} which resolves to a {@link Measurement} as soon as it's fully loaded. Wait
     *         for the {@code Promise} to complete before calling {@code #next()} again.
     */
    @Override
    public Future<Measurement> next() {
        try {
            Validate.isTrue(initialized, "Calling next() before initialized is not supported!");
            pendingNextCall.acquire();
            nextMeasurement = Promise.promise();

            // Resume bucket loading. The promise is resolved when all measurement buckets have been loaded.
            bucketStream.fetch(1);
            return nextMeasurement.future();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Resolves the {@link #pendingNextCall} {@code Promise} when all data of the next measurement was loaded after a
     * {@link #next()} call.
     *
     * @param buckets the {@link TrackBucket}s of the measurement requested.
     * @param nextMeasurementId the {@link MeasurementIdentifier} of the measurement requested.
     * @param nextBucket the {@code TrackBucket} of the next measurement, to be cached.
     */
    private void resolveNext(final List<TrackBucket> buckets, final MeasurementIdentifier nextMeasurementId,
            final TrackBucket nextBucket) {
        final var measurement = measurement(buckets);

        // Prepare cache for the next measurement
        cachedBuckets.clear();
        cachedMeasurementId = nextMeasurementId;
        if (nextBucket != null) {
            cachedBuckets.add(nextBucket);
        }

        // Prepare for next `next()` call
        bucketStream.pause();
        final var promise = nextMeasurement;
        nextMeasurement = null;
        pendingNextCall.release();

        // Resolve `nextMeasurement` promise
        promise.complete(measurement);
    }

    /**
     * Constructs a new measurement from a document loaded from the database.
     *
     * @param buckets The data to load the measurement from
     * @return The newly created {@link Measurement}
     */
    Measurement measurement(final List<TrackBucket> buckets) {
        if (buckets.isEmpty()) {
            throw new IllegalArgumentException("Cannot create a measurement from 0 buckets!");
        }

        final var tracks = tracks(buckets);
        return new Measurement(buckets.get(0).getMetaData(), tracks);
    }

    /**
     * Merges {@link TrackBucket}s into {@link Track}s.
     *
     * @param trackBuckets the data to merge
     * @return the tracks
     */
    List<Track> tracks(final List<TrackBucket> trackBuckets) {

        // Group by trackId
        final var groupedBuckets = trackBuckets.stream()
                .collect(groupingBy(TrackBucket::getTrackId));

        // Sort bucket groups by trackId
        final var sortedBucketGroups = groupedBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));

        // Convert buckets to Track
        final var tracks = new ArrayList<Track>();
        sortedBucketGroups.forEach((trackId, bucketGroup) -> {
            // Sort buckets
            final var sortedBuckets = bucketGroup.stream()
                    .sorted(Comparator.comparing(TrackBucket::getBucket))
                    .collect(toList());

            // Merge buckets
            final var locations = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getLocationRecords().stream())
                    .collect(Collectors.toList());
            final var accelerations = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getAccelerations().stream())
                    .collect(Collectors.toList());
            final var rotations = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getRotations().stream())
                    .collect(Collectors.toList());
            final var directions = sortedBuckets.stream()
                    .flatMap(bucket -> bucket.getTrack().getDirections().stream())
                    .collect(Collectors.toList());

            final var track = new Track(locations, accelerations, rotations, directions);
            tracks.add(track);
        });
        return tracks;
    }

    /**
     * A handler to be called after the last bucket was loaded from the {@link #bucketStream} to construct and return
     * the last measurement to the caller.
     *
     * @author Armin Schnabel
     * @since 6.0.0
     */
    private static class LastMeasurementHandler implements Handler<Void> {

        /**
         * The {@link MeasurementIterator} which is bound to this {@code Handler}.
         */
        private final MeasurementIterator caller;

        /**
         * Constructs a fully initialized instance of this class
         *
         * @param caller The {@link MeasurementIterator} which is bound to this {@code Handler}.
         */
        private LastMeasurementHandler(final MeasurementIterator caller) {
            this.caller = caller;
        }

        @Override
        public void handle(Void ended) {
            final var isNextPromisePending = caller.nextMeasurement != null;
            if (isNextPromisePending) {
                Validate.notEmpty(caller.cachedBuckets,
                        "Stream ended with a pending nextMeasurement promise and an empty cache");
                // The stream ended, i.e. the remaining cached buckets are the last measurement
                caller.resolveNext(caller.cachedBuckets, null, null);
            }

            // Clear cache, which also ensures that `hasNext()` returns `false
            caller.cachedMeasurementId = null;
            caller.cachedBuckets.clear();

            // No entries found for the requested data
            if (!caller.initialized) {
                caller.initializedHandler.handle(caller);
            }
        }
    }
}
