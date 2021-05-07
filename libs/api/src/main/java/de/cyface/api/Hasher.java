/*
 * Copyright 2021 Cyface GmbH
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

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.auth.mongo.HashAlgorithm;

/**
 * Used to properly hash passwords for storage to the database.
 * 
 * @author Klemens Muthmann
 * @version 1.0.0
 */
public class Hasher {

    /**
     * The Vertx <code>HashingStrategy</code> used for hashing.
     */
    private final HashingStrategy hashingStrategy;
    /**
     * A salt used to obfuscate the hashed password, making it harder to decrypt passwords if the database is
     * compromised.
     */
    private final byte[] salt;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param hashingStrategy The Vertx <code>HashingStrategy</code> used for hashing
     * @param salt A salt used to obfuscate the hashed password, making it harder to decrypt passwords if the database
     *            is compromised
     */
    public Hasher(final HashingStrategy hashingStrategy, final byte[] salt) {
        Objects.requireNonNull(hashingStrategy);
        Objects.requireNonNull(salt);

        this.hashingStrategy = hashingStrategy;
        this.salt = Arrays.copyOf(salt, salt.length);
    }

    /**
     * Hashes the provided password, according to this strategy instance.
     *
     * @param password The clear text password to hash
     * @return The hashed and salted password
     */
    public String hash(final String password) {
        return hashingStrategy.hash(HashAlgorithm.PBKDF2.name().toLowerCase(), // FIXME: stronger hashing algorithm recommended
                null,
                Base64.getMimeEncoder().encodeToString(salt),
                password);
    }
}
