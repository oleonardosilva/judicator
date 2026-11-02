package com.elless.judicator.api.player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An account, with the heaviest punishment it has in force.
 *
 * @param name     the name it last had
 * @param uniqueId its UUID; empty for a name nobody had seen (offline mode)
 * @param status   what it has in force
 */
public record AccountView(String name, Optional<UUID> uniqueId, AccountStatus status) {

    public AccountView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(status, "status");
    }
}
