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
package de.cyface.collector.storage.exception

import java.util.Objects

/**
 * An `Exception` thrown when the provided content range of an uploaded file does not correspond to the content range,
 * provided via the HTTP header.
 *
 * @author Klemens Muthmann
 * @version 1.0.1
 * @constructor Provide an explanation for the error via the `format` parameter.
 */
class ContentRangeNotMatchingFileSize(format: String) : Exception(format) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentRangeNotMatchingFileSize) return false
        return this.message == other.message
    }

    override fun hashCode(): Int {
        return Objects.hash(javaClass.hashCode(), message)
    }
}
