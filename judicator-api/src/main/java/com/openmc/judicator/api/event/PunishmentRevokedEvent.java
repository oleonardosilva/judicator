package com.openmc.judicator.api.event;

import com.openmc.judicator.api.punishment.PunishmentView;

import java.util.Objects;

/**
 * A ban or mute was revoked ({@code /unban}, {@code /unmute}, {@code /revoke}, {@code /staffrollback} or the API).
 * Revoking several punishments at once posts one event for each.
 *
 * @param punishment the punishment, now {@link com.openmc.judicator.api.punishment.PunishmentStatus#REVOKED} (the
 *                   reason is in {@link PunishmentView#revokedReason()})
 * @param revokedBy  who revoked it: a staff member, {@code Console}, or the name a plugin gave
 */
public record PunishmentRevokedEvent(PunishmentView punishment, String revokedBy) implements JudicatorEvent {

    public PunishmentRevokedEvent {
        Objects.requireNonNull(punishment, "punishment");
        Objects.requireNonNull(revokedBy, "revokedBy");
    }

}
