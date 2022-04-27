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

import org.apache.commons.lang3.Validate;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A user for testing representing a user that was created via the user registration process and thus requires e-mail
 * verification and activation.
 * 
 * @author Klemens Muthmann
 */
public final class ActivatableTestUser extends TestUser {

    /**
     * {@code True} if the user account is activated, {@code False} if the user signed up but did not activate the
     * account.
     */
    private final boolean activated;
    /**
     * The token to activate the user account if the user signed up.
     */
    private final String activationToken;

    /**
     * Creates a fully initialized instance of this class.
     *
     * @param username The name of the user to be created
     * @param roles The roles this user has
     * @param password The password used to authenticate the user
     * @param activated {@code True} if the user account is activated, {@code False} if the user signed up but did not
     *            activate the account.
     * @param activationToken The token to activate the user accounts.
     */
    public ActivatableTestUser(final String username, final String password, final boolean activated,
            final String activationToken,
            final String... roles) {
        super(username, password, roles);
        Validate.notEmpty(activationToken);

        this.activated = activated;
        this.activationToken = activationToken;
    }

    @Override
    protected JsonObject insertCommand() {
        return new JsonObject()
                .put("username", getUsername())
                .put("roles", new JsonArray(getRoles()))
                .put("password", getHashedPassword())
                .put("activated", activated)
                .put("activationToken", activationToken);
    }
}
