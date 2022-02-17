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
package de.cyface.api;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * This class checks the inner workings of the {@link Authenticator}.
 *
 * @author Armin Schnabel
 * @since 6.2.0
 */
@SuppressWarnings({"SpellCheckingInspection"})
public class AuthenticatorTest {

    /**
     * Only self-registered accounts need to be activated and, thus, a missing "activated" field counts as activated.
     */
    @Test
    void testActivated() {
        // Arrange
        final var registeredPrincipal = new JsonObject().put("username", "guest").put("activated", false);
        final var activatedPrincipal = new JsonObject().put("username", "guest").put("activated", true);
        final var createdPrincipal = new JsonObject().put("username", "guest");

        // Act
        final var registeredResult = Authenticator.activated(registeredPrincipal);
        final var activatedResult = Authenticator.activated(activatedPrincipal);
        final var createdResult = Authenticator.activated(createdPrincipal);

        // Assert
        assertThat("Check activation", registeredResult, is(equalTo(false)));
        assertThat("Check activation", activatedResult, is(equalTo(true)));
        assertThat("Check activation", createdResult, is(equalTo(true)));
    }
}
