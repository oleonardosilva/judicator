package com.openmc.judicator.api.punishment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A punishment as it was stored when it was read: an immutable snapshot, it does not follow later changes (read it
 * again, or listen to the events, for that).
 *
 * @param id              the punishment id shown by {@code /pview}
 * @param type            what it does
 * @param targetName      the name of the punished account, as it was when punished
 * @param targetUniqueId  the UUID of the punished account; empty when it was not known (a player who never joined
 *                        and could not be looked up)
 * @param ipAddress       the address of an IP punishment ({@code /banip}, {@code /muteip}); empty for a punishment of
 *                        the account only
 * @param punisher        who applied it: a staff member, {@code Console}, or the name a plugin gave
 * @param reason          the reason; empty when none was given
 * @param startedAt       when it was applied
 * @param endsAt          when it ends; empty for a permanent punishment and while an online-only mute is paused
 * @param status          where it stood when it was read
 * @param revokedReason   the reason it was revoked with, when it was revoked with one
 * @param evidences       the evidence links attached to it
 * @param permanent       whether it has no end
 * @param soft            a soft mute: the player still sees their own messages, nobody else does
 * @param onlineOnly      a temporary mute whose time only runs while the player is online
 * @param pausedRemaining what is left of an online-only mute while the player is offline (its time is stopped)
 */
public record PunishmentView(long id,
                             PunishmentType type,
                             String targetName,
                             Optional<UUID> targetUniqueId,
                             Optional<String> ipAddress,
                             String punisher,
                             String reason,
                             Instant startedAt,
                             Optional<Instant> endsAt,
                             PunishmentStatus status,
                             Optional<String> revokedReason,
                             List<String> evidences,
                             boolean permanent,
                             boolean soft,
                             boolean onlineOnly,
                             Optional<Duration> pausedRemaining) {

    public PunishmentView {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetUniqueId, "targetUniqueId");
        Objects.requireNonNull(ipAddress, "ipAddress");
        Objects.requireNonNull(punisher, "punisher");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(revokedReason, "revokedReason");
        Objects.requireNonNull(pausedRemaining, "pausedRemaining");
        evidences = List.copyOf(evidences);
    }

    /**
     * @return whether it was in force when it was read
     */
    public boolean isActive() {
        return status == PunishmentStatus.ACTIVE;
    }

    /**
     * @return whether it is an IP punishment (it reaches every account on {@link #ipAddress()})
     */
    public boolean isByAddress() {
        return ipAddress.isPresent();
    }

    /**
     * @return whether it is an online-only mute whose time is stopped because the player is offline
     */
    public boolean isPaused() {
        return pausedRemaining.isPresent();
    }

}
