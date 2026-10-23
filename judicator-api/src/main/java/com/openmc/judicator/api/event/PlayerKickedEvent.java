package com.openmc.judicator.api.event;

import com.openmc.judicator.api.punishment.PunishmentView;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A player was kicked by Judicator ({@code /kick} or the API). Logins refused because of a ban are not kicks.
 *
 * @param targetName     the name of the kicked player
 * @param targetUniqueId the UUID of the kicked player
 * @param punisher       who kicked: a staff member, {@code Console}, or the name a plugin gave
 * @param reason         the reason ({@code None} when none was given)
 * @param silent         whether it was not announced to the players
 * @param logged         the kick as stored in the history, with {@code log-kicks: true}; empty otherwise
 */
public record PlayerKickedEvent(String targetName,
                                UUID targetUniqueId,
                                String punisher,
                                String reason,
                                boolean silent,
                                Optional<PunishmentView> logged) implements JudicatorEvent {

    public PlayerKickedEvent {
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetUniqueId, "targetUniqueId");
        Objects.requireNonNull(punisher, "punisher");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(logged, "logged");
    }

}
