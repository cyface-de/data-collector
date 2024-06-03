/*
 * Copyright 2022-2024 Cyface GmbH
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
@file:Suppress("AnnotationSpacing")

package de.cyface.collector.storage

import de.cyface.collector.model.ContentRange
import de.cyface.collector.model.RequestMetaData
import de.cyface.collector.model.User
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import java.util.UUID

/**
 * Implementations of this interface realize the storage of uploaded measurements, and provide information about the
 * status of the upload.
 *
 * Each upload is associated with an `uploadIdentifier`. This identifier can be used to repeat uploads if interrupted
 * and to ask for status information. After an upload is complete, the `uploadIdentifier` becomes invalid.
 *
 * @author Klemens Muthmann
 */
interface DataStorageService {
    /**
     * Stores the data provided via `sourceData`.
     *
     * @param sourceData A Vert.x `ReadStream` with the data to upload.
     * @param uploadMetaData Information required to store the uploaded data properly.
     * @return A `Future` providing the ``Status`` of the upload, when it has finished.
     */
    fun store(
        sourceData: ReadStream<Buffer>,
        uploadMetaData: UploadMetaData
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
     * @param uploadExpirationTime The time after which temporary data files are stale and eligible for cleaning in
     * milliseconds.
     * @param vertx The `Vertx` instance to place the background process at.
     * @param cleanupOperation An algorithm, that describes exactly how to clean up the data storage.
     */
    fun startPeriodicCleaningOfTempData(uploadExpirationTime: Long, vertx: Vertx, cleanupOperation: CleanupOperation)

    /**
     * Check the data storage on whether some measurement is already stored.
     *
     * @param deviceId The worldwide unique device identifier of the measurement to check.
     * @param measurementId The device wide unique identifier of the measurement to check.
     * @return A `Future` telling the caller asynchronously whether the measurement is already stored or not. If it is
     * the result will be `true` and `false` otherwise.
     */
    fun isStored(deviceId: String, measurementId: Long): Future<Boolean>
}

/**
 * A class summarizing the parameters required for data storage in addition to the raw data.
 *
 * @author Klemens Muthmann
 * @property user The user who took the measurement.
 * @property contentRange The content range of the uploaded data as provided by the content-range HTTP header.
 * @property uploadIdentifier The cluster wide unique identifier of the upload.
 * @property metaData Meta information provided alongside the measurement, to get insights into the measurement,
 * without the need to deserialize the data.
 */
class UploadMetaData(
    val user: User,
    val contentRange: ContentRange,
    val uploadIdentifier: UUID,
    val metaData: RequestMetaData
)
