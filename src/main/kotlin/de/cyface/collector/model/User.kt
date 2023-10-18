/*
 * Copyright 2022-2023 Cyface GmbH
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
package de.cyface.collector.model

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.mongo.MongoAuthorization
import java.util.Objects
import java.util.UUID

/**
 * This class represents a user.
 *
 * @author Armin Schnabel
 * @version 2.0.0
 * @since 7.1.0
 * @property id The identifier of the user.
 * @property name The username of the user.
 */
class User(
    val id: UUID,
    val name: String
) {

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param databaseValue A role entry from the database.
     */
    constructor(databaseValue: JsonObject) : this(
        UUID.fromString(databaseValue.getString("_id")),
        name = databaseValue.getString(MongoAuthorization.DEFAULT_USERNAME_FIELD)
    )

    /**
     * @return The identifier of the user as `String`.
     */
    val idString: String
        get() = id.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val user = other as User
        return id == user.id && name == user.name
    }

    override fun hashCode(): Int {
        return Objects.hash(name)
    }
}
