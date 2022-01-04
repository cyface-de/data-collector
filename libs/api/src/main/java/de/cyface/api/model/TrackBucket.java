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
package de.cyface.api.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.cyface.model.MetaData;
import de.cyface.model.Track;
import io.vertx.core.json.JsonObject;

/**
 * The mongo database "bucket" which contains a slice of a track.
 *
 * @author Armin Schnabel
 * @since 6.0.0
 */
public class TrackBucket {

    /**
     * The track's position within the measurement.
     */
    final int trackId;
    /**
     * The time "slice" of the bucket.
     */
    final Date bucket;
    /**
     * The track slice of the bucket.
     */
    final Track track;
    /**
     * The {@link MetaData} of the track.
     */
    final MetaData metaData;

    /**
     * Initialized a fully constructed instance of this class.
     *
     * @param trackId The track's position within the measurement.
     * @param bucket The time "slice" of the bucket.
     * @param track The track slice of the bucket.
     * @param metaData The {@link MetaData} of the track.
     * @throws ParseException thrown if the {@link #bucket} date cannot be parsed.
     */
    public TrackBucket(final int trackId, final JsonObject bucket, final Track track, final MetaData metaData)
            throws ParseException {
        // noinspection SpellCheckingInspection
        this(trackId, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(bucket.getString("$date")), track,
                metaData);
    }

    /**
     * Initialized a fully constructed instance of this class.
     *
     * @param trackId The track's position within the measurement.
     * @param bucket The time "slice" of the bucket.
     * @param track The track slice of the bucket.
     * @param metaData The {@link MetaData} of the track.
     */
    public TrackBucket(final int trackId, final Date bucket, final Track track, final MetaData metaData) {
        this.trackId = trackId;
        this.bucket = bucket;
        this.track = track;
        this.metaData = metaData;
    }

    /**
     * @return The track's position within the measurement.
     */
    public int getTrackId() {
        return trackId;
    }

    /**
     * @return The time "slice" of the bucket.
     */
    public Date getBucket() {
        return bucket;
    }

    /**
     * @return The track slice of the bucket.
     */
    public Track getTrack() {
        return track;
    }

    /**
     * @return The {@link MetaData} of the track.
     */
    public MetaData getMetaData() {
        return metaData;
    }
}