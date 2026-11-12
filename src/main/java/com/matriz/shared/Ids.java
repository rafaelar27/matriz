package com.matriz.shared;

import java.util.Optional;
import java.util.UUID;

/** Identifiers are UUIDs. A value that is not a valid UUID cannot identify anything. */
public final class Ids {

    private Ids() {
    }

    public static Optional<UUID> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
