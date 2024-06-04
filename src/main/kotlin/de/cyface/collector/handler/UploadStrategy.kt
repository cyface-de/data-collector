/*
 * Copyright 2024 Cyface GmbH
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
package de.cyface.collector.handler

import io.vertx.core.Future

/**
 * Interface for checking conflicts with measurement or attachment uploads and the persistent storage.
 */
interface UploadStrategy {
    /**
     * Checks if the measurement with the given id is already stored in the database.
     *
     * Depending on the implementation this might include checking for the existence of the measurement itself or
     * also for the existence of attachments.
     *
     * @param deviceId The id of the device that uploaded the measurement.
     * @param measurementId The id of the measurement to check.
     * @param attachmentId The id of the attachment to check. If null, only the measurement is checked.
     * @return A future that will be completed with true if the check are successful. Depending on the implementation
     * this might mean that the measurement does or does not yet exist or that the attachment does not yet exist.
     */
    fun checkConflict(deviceId: String, measurementId: String, attachmentId: String?): Future<Boolean>
}
