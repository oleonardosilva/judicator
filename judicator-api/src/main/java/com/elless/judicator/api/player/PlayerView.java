package com.elless.judicator.api.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An account that joined a server running Judicator.
 *
 * @param uniqueId its UUID
 * @param name     the name it last joined with
 * @param lastSeen when it last joined
 */
public record PlayerView(UUID uniqueId, String name, Instant lastSeen) {

    public PlayerView {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lastSeen, "lastSeen");
    }
}
