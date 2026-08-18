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

import io.vertx.core.json.JsonObject

/*
 * Readers for documents which were written by an arbitrary earlier version of the software.
 *
 * Upload metadata arrives as strings and is stored partly as strings and partly as numbers, depending on the version
 * which wrote it. Reading such a document back with the strict `JsonObject.getLong` and friends throws a
 * `ClassCastException` on the first entry that used the other representation. These readers accept both and answer
 * `null` where a strict reader would fail, which suits callers that must not break over a legacy document.
 *
 * They are `internal` on purpose: the storage implementations share them across their packages, but they are no
 * promise made to users of this library.
 */

/**
 * Reads [key] as a `String`, whatever type it was stored as.
 */
internal fun JsonObject.lenientString(key: String): String? = getValue(key)?.toString()

/**
 * Reads [key] as a `Long`, accepting both numeric and string representations.
 */
internal fun JsonObject.lenientLong(key: String): Long? = when (val value = getValue(key)) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong()
    else -> null
}

/**
 * Reads [key] as an `Int`, accepting both numeric and string representations.
 */
internal fun JsonObject.lenientInt(key: String): Int? = lenientLong(key)?.toInt()

/**
 * Reads [key] as a `Double`, accepting both numeric and string representations.
 */
internal fun JsonObject.lenientDouble(key: String): Double? = when (val value = getValue(key)) {
    is Number -> value.toDouble()
    is String -> value.toDoubleOrNull()
    else -> null
}

/**
 * Reads [key] as a date.
 *
 * The Vert.x Mongo client represents a BSON date as a `{"$date": "<ISO 8601>"}` sub document, while a plain string is
 * used by some drivers and by test doubles.
 */
internal fun JsonObject.lenientDate(key: String): String? = when (val value = getValue(key)) {
    is JsonObject -> value.getString("\$date") ?: value.encode()
    null -> null
    else -> value.toString()
}
