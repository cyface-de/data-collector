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

/**
 * Interface for all the methods required by a Cloud storage solution.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface CloudStorage {
    /**
     * Writes some bytes to the Cloud storage.
     */
    fun write(bytes: ByteArray)

    /**
     * Deletes the data from the Cloud storage.
     */
    fun delete()

    /**
     * Quries the Cloud for the amount of bytes currently stored.
     */
    fun bytesUploaded(): Long
}
