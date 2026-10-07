/*
 * Copyright 2026 Cyface GmbH
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

/**
 * A storage independent view on the metadata of an upload which is already stored.
 *
 * Each [DataStorageService] keeps its metadata in its own shape and is responsible for translating that shape into
 * this class. Callers work with the result without knowing which storage backend produced it, so a new backend is
 * added by implementing [DataStorageService] alone.
 *
 * Every property is nullable, because a stored document was written by some arbitrary earlier version of the
 * software and need not carry all of today's fields.
 *
 * @author Klemens Muthmann
 * @property deviceType The type of device which captured the upload, such as `Pixel 3`.
 * @property operatingSystemVersion The operating system version of the capturing device, such as `Android 9.0.0`.
 * @property applicationVersion The version of the application which transmitted the upload.
 * @property formatVersion The format version of the transfer file.
 * @property length The length of the measurement in meters.
 * @property locationCount The count of geographical locations in the measurement.
 * @property startLocationTimestamp The timestamp in milliseconds of the first location of the measurement.
 * @property endLocationTimestamp The timestamp in milliseconds of the last location of the measurement.
 * @property modality The modality used to capture the measurement.
 * @property uploadDate The point in time the upload was stored, if the storage keeps such a value.
 */
data class StoredMetaData(
    val deviceType: String? = null,
    val operatingSystemVersion: String? = null,
    val applicationVersion: String? = null,
    val formatVersion: Int? = null,
    val length: Double? = null,
    val locationCount: Long? = null,
    val startLocationTimestamp: Long? = null,
    val endLocationTimestamp: Long? = null,
    val modality: String? = null,
    val uploadDate: String? = null,
)
