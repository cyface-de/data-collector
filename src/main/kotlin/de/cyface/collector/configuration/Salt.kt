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
package de.cyface.collector.configuration

import io.vertx.core.Future
import io.vertx.core.Vertx

/**
 * An encryption salt used to complicate brute force attacks on user passwords.
 *
 * Cyface currently uses the same application wide salt value.
 * This is not the most secure way since it is reversible if someone gets their hands on enough passwords.
 * However, at the time of implementation Vertx only supported application wide salts.
 * This has changed with recent Vertx version.
 * A future implementation of Cyface will most likely switch to per user salts.
 *
 * @author Klemens Muthmann
 * @version 1.0.0
 */
interface Salt {
    /**
     * Provide the salt as an array of bytes.
     *
     * @param vertx The Vertx instance currently used to run this application.
     * @return A future containing the salt as a byte array upon successful completion of this operation.
     */
    fun bytes(vertx: Vertx): Future<ByteArray>
}
