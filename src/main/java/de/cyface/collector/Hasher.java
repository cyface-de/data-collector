package de.cyface.collector;

import java.util.Base64;
import java.util.Objects;

import io.vertx.ext.auth.HashingStrategy;

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
     * A salt used to obfuscate the hased password, making it harder to decrypt passwords if the database is
     * compromised.
     */
    private final byte[] salt;

    /**
     * Creates a new completely initialized object of this class.
     *
     * @param hashingStrategy The Vertx <code>HashingStrategy</code> used for hashing
     * @param salt A salt used to obfuscate the hased password, making it harder to decrypt passwords if the database is
     *            compromised
     */
    public Hasher(final HashingStrategy hashingStrategy, final byte[] salt) {
        Objects.requireNonNull(hashingStrategy);
        Objects.requireNonNull(salt);

        this.hashingStrategy = hashingStrategy;
        this.salt = salt;
    }

    /**
     * Hashes the provided password, according to this strategy instance.
     *
     * @param password The clear text password to hash
     * @return The hashed and salted password
     */
    public String hash(final String password) {
        return hashingStrategy.hash("pbkdf2",
                null,
                Base64.getMimeEncoder().encodeToString(salt),
                password);
    }
}
