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
package de.cyface.apitestutils.fixture.user;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A test user created the old way via the administration API. Such a user requires no verification or registration
 * E-Mail.
 * 
 * @author Klemens Muthmann
 */
public class DirectTestUser extends TestUser {
    /**
     * Creates a fully initialized instance of this class.
     *
     * @param username The name of the user to be created
     * @param password The password used to authenticate the user
     * @param roles The roles this user has
     */
    public DirectTestUser(String username, String password, String... roles) {
        super(username, password, roles);
    }

    @Override
    protected JsonObject insertCommand() {
        return new JsonObject()
                .put("username", getUsername())
                .put("roles", new JsonArray(getRoles()))
                .put("password", getHashedPassword());
    }
}
