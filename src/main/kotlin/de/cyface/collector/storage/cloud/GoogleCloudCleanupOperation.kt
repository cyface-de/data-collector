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
 * It is pretty safe to assume, that stale uploads are not going to continue. If the deletion was wrong, the client,
 * can restart with a fresh upload and try to finish sooner this time.
 * To make this work it is important to set `fileExpirationTime` high enough, so that all clients are capable of
 * finishing within that timeframe.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 * @property storage The [Storage] to access the Google Cloud.
 * @property bucketName The Google Cloud Storage bucket name used to store data to.
 */
class GoogleCloudCleanupOperation(
    private val storage: Storage,
    private val bucketName: String
) : CleanupOperation {

    /**
     * A regular expression for finding the name of an upload from it temporary representation.
     */
    private val nameExtraction = "(.+)\\.tmp".toRegex()

    override fun clean(fileExpirationTime: Long) {
        val currentTime = OffsetDateTime.now()
        // Even when we assign a custom Role with `storage.buckets.list` to the service account, we get the error:
        // `does not have storage.buckets.list access` (even with all storage permission)
        // Thus, we only clean up the bucket with the name injected, and add a custom role with `storage.buckets.get`.
        // It anyway makes more sense to only clean up the bucket used by this collector, not all buckets.
        // val storedFiles = storage.list(Storage.BucketListOption.pageSize(pagingSize))
        // storedFiles.iterateAll().forEach { bucket ->

        val bucket = storage.get(bucketName)
        val expirationTime = fileExpirationTime * NANO_SECONDS_IN_A_MILLISECOND

        val updateTime = bucket.updateTimeOffsetDateTime
        val expiredTimeSinceLastUpdate = currentTime.minus(
            updateTime.toEpochSecond() * MILLI_SECONDS_IN_A_SECOND,
            ChronoUnit.MILLIS
        )
        val expiredTimeInNanos = expiredTimeSinceLastUpdate.nano

        if (expiredTimeInNanos < expirationTime) {
            return
        }

        if (bucket.name.endsWith(".tmp")) {
            val uploadIdentifier = nameExtraction.matchEntire(bucket.name)?.groups?.get(1)?.value ?: return

            val dataName = "$uploadIdentifier.data"

            storage.delete(bucket.name)
            storage.delete(dataName)
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
