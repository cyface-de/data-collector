package de.cyface.collector;

import java.util.Base64;
import java.util.Objects;

import io.vertx.ext.auth.HashingStrategy;

public class Hasher {

    private final HashingStrategy hashingStrategy;
    private final byte[] salt;

    public Hasher(final HashingStrategy hashingStrategy, final byte[] salt) {
        Objects.requireNonNull(hashingStrategy);
        Objects.requireNonNull(salt);

        this.hashingStrategy = hashingStrategy;
        this.salt = salt;
    }

    public String hash(final String password) {
        return hashingStrategy.hash("pbkdf2",
                null,
                Base64.getMimeEncoder().encodeToString(salt),
                password);
    }
}
