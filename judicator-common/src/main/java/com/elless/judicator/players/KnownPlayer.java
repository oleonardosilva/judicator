package com.elless.judicator.players;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An account that joined: its UUID, the name it last joined with and when.
 */
public record KnownPlayer(UUID uuid, String name, LocalDateTime lastSeen) {
}
