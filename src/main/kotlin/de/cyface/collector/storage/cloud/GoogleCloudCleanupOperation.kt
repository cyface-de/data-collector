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
import java.util.Locale

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

    override fun clean(fileExpirationTime: Long) {
        val currentTime = OffsetDateTime.now()
        val bucket = storage.get(bucketName)
        require(bucket != null) { String.format(Locale.getDefault(), "Bucket with name %s not found", bucketName) }

        bucket.list().iterateAll().forEach { blob ->
            if (!blob.name.endsWith("/tmp")) return@forEach

            val updateTime = blob.updateTimeOffsetDateTime ?: return@forEach
            val millisSinceLastUpdate = ChronoUnit.MILLIS.between(updateTime, currentTime)
            if (millisSinceLastUpdate < fileExpirationTime) return@forEach

            val uploadIdentifier = blob.name.removeSuffix("/tmp")
            storage.delete(bucketName, blob.name)
            storage.delete(bucketName, "$uploadIdentifier/data")
        }
    }
}
