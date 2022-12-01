/*
 * Copyright 2022 Cyface GmbH
 *
 * This file is part of the Serialization.
 *
 * The Serialization is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Serialization is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Serialization. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.storage.cloud

import com.google.cloud.storage.Storage
import de.cyface.collector.storage.CleanupOperation
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * A [CleanupOperation] for the Google Cloud Store.
 *
 * Works together with [GoogleCloudStorageService].
 *
 * The operation looks for temporary files ending in `.tmp`. Since those files only exist for not yet finished uploads,
 * it deletes such files if it finds and if their update time exceeds `fileExpirationTime`. It also deletes the
 * data file belonging to the temporary file, since that file will only contain partially completed data.
 *
 * It is pretty save to assume, that stale uploads are not going to continue. If the deletion was wrong, the client,
 * can restart with a fresh upload and try to finish sooner this time.
 * To make this work it is important to set `fileExpirationTime` high enough, so that all clients are capable of
 * finishing within that timeframe.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property storage The [Storage] to access the Google Cloud.
 * @property pagingSize A parameter used by Google Cloud for the amount of buckets returned per single request.
 * Higher numbers increase the possibility of failure, since requests and responses get larger, but also decrease the
 * number of requests necessary to carry out operations on the Cloud. On stable connections try high numbers on instable
 * ones try lower numbers.
 */
class GoogleCloudCleanupOperation(
    private val storage: Storage,
    private val pagingSize: Long
) : CleanupOperation {

    /**
     * A regular expression for finding the name of an upload from it temporary representation.
     */
    private val nameExtraction = "(.+)\\.tmp".toRegex()

    override fun clean(fileExpirationTime: Long) {
        val currentTime = OffsetDateTime.now()
        val storedFiles = storage.list(Storage.BucketListOption.pageSize(pagingSize))
        val expirationTime = fileExpirationTime * NANO_SECONDS_IN_A_MILLISECOND
        storedFiles.iterateAll().forEach { bucket ->

            val updateTime = bucket.updateTimeOffsetDateTime
            val expiredTimeSinceLastUpdate = currentTime.minus(
                updateTime.toEpochSecond() * MILLI_SECONDS_IN_A_SECOND,
                ChronoUnit.MILLIS
            )
            val expiredTimeInNanos = expiredTimeSinceLastUpdate.nano

            if (expiredTimeInNanos < expirationTime) {
                return@forEach
            }

            if (bucket.name.endsWith(".tmp")) {
                val uploadIdentifier = nameExtraction.matchEntire(bucket.name)?.groups?.get(1)?.value ?: return@forEach

                val dataName = "$uploadIdentifier.data"

                storage.delete(bucket.name)
                storage.delete(dataName)
            }
        }
    }

    companion object {
        /**
         * The amount of nanoseconds in a single second. This is required to convert the file expiration time.
         */
        const val NANO_SECONDS_IN_A_MILLISECOND = 1_000_000L

        /**
         * The amount of milliseconds in a single second. This is required to convert the update time of a bucket.
         */
        const val MILLI_SECONDS_IN_A_SECOND = 1_000L
    }
}
