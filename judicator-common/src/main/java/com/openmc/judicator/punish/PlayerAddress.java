package com.openmc.judicator.punish;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * An account joined from an IP address: what {@code /alts}, {@code alts.deny-if-banned} and IP punishments of offline
 * players read.
 *
 * @param uuid      the account's UUID; null for a record copied from 1.x whose account is unknown (it gets the UUID
 *                  when that name joins)
 * @param nickname  the name it last joined with from this address
 * @param lastUsage when it last joined from this address
 */
public record PlayerAddress(Long id, UUID uuid, String nickname, String hostAddress, LocalDateTime lastUsage) {

    public Optional<UUID> getUuid() {
        return Optional.ofNullable(uuid);
    }

}
