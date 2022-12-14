@file:Suppress("AnnotationSpacing")

/*
 * Copyright 2022 Cyface GmbH
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
package de.cyface.collector.storage

import de.cyface.api.model.User
import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.Pipe
import java.util.UUID

/**
 * Implementations of this interface realize the storage of uploaded measurements, and provide information about the
 * status of the upload.
 *
 * Each upload is associated with an `uploadIdentifier`. This identifier can be used to repeat uploads if interrupted
 * and to ask for status information. After an upload is complete, the `uploadIdentifier` becomes invalid.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface DataStorageService {
    /**
     * Stores the data provided via the `pipe`.
     *
     * @param pipe A Vert.x `Pipe` with the data to upload.
     * @param user The user who took the measurement.
     * @param contentRange The content range of the uploaded data as provided by the content-range HTTP header.
     * @param uploadIdentifier The cluster wide unique identifier of the upload.
     * @param metaData Meta information provided alongside the measurement, to get insights into the measurement,
     * without the need to deserialize the data.
     * @return A `Future` providing the ``Status`` of the upload, when it has finished.
     */
    fun store(
        pipe: Pipe<Buffer>,
        user: User,
        contentRange: ContentRange,
        uploadIdentifier: UUID,
        metaData: RequestMetaData
    ): Future<Status>

    /**
     * Provides the amount of already uploaded bytes for the provided `uploadIdentifier`.
     *
     * @param uploadIdentifier The cluster wide unique identifier of the upload.
     * @return The number of already uploaded bytes.
     */
    fun bytesUploaded(uploadIdentifier: UUID): Future<Long>

    /**
     * Remove the already uploaded temporary data for this upload.
     *
     * @param uploadIdentifier The `uploadIdentifier` to remove the temporary data for.
     * @return A `Future` which informs the caller, asynchronously about completion of the operation.
     */
    fun clean(uploadIdentifier: UUID): Future<Void>

    @Suppress("ForbiddenComment")
    // TODO: This should probably be its own verticle.
    /**
     * Start a background timer, which cleans all temporary files not associated with an active upload anymore.
     *
     * @param uploadExpirationTime The time after which temporary data files are stale and eligible for cleaning.
     * @param vertx The `Vertx` instance to place the background process at.
     * @param cleanupOperation An algorithm, that describes exactly how to clean up the data storage.
     */
    fun startPeriodicCleaningOfTempData(uploadExpirationTime: Long, vertx: Vertx, cleanupOperation: CleanupOperation)

    /**
     * Check the data storage on whether some measurement is already stored.
     *
     * @param deviceId The world wide unique device identifier of the measurement to check.
     * @param measurementId The device wide unique identifier of the measurement to check.
     * @return A `Future` telling the caller asynchronously whether the measurement is already stored or not. If it is
     * the result will be `true` and `false` otherwise.
     */
    fun isStored(deviceId: String, measurementId: Long): Future<Boolean>
}
