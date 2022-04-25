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
package de.cyface.api.model;

import io.vertx.core.json.JsonObject;
import org.bson.types.ObjectId;

import java.util.Objects;

import static io.vertx.ext.auth.mongo.MongoAuthorization.DEFAULT_USERNAME_FIELD;

/**
 * This class represents a user.
 *
 * @author Armin Schnabel
 * @since 6.6.0
 * @version 1.0.0
 */
public class User {

    /**
     * The identifier of the {@link User}.
     */
    private final ObjectId id;
    /**
     * The username of the {link User}.
     */
    private final String name;

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param id The identifier of the {@link User}.
     * @param name The username of the {link User}.
     */
    public User(final ObjectId id, final String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Constructs a fully initialized instance of this class.
     *
     * @param databaseValue A role entry from the database.
     */
    public User(final JsonObject databaseValue) {
        this.id = new ObjectId(databaseValue.getString("_id"));
        this.name = databaseValue.getString(DEFAULT_USERNAME_FIELD);
    }

    /**
     * @return The identifier of the {@link User}.
     */
    public ObjectId getId() {
        return id;
    }

    /**
     * @return The username of the {link User}.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        User user = (User)o;
        return id.equals(user.id) && name.equals(user.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
