package com.openmc.judicator.integration;

import com.openmc.judicator.api.punishment.PunishmentStatus;
import com.openmc.judicator.api.punishment.PunishmentType;
import com.openmc.judicator.api.punishment.PunishmentView;
import com.openmc.judicator.api.warn.WarnStatus;
import com.openmc.judicator.api.warn.WarnView;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.warns.Warn;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Turns the core's punishments and warns into the immutable views of the API. The core keeps times as
 * {@link LocalDateTime} in the server's time zone; the API gives {@link Instant}s.
 */
public final class ApiViews {

    private ApiViews() {
    }

    public static PunishmentView punishment(Punishment punishment) {
        return punishment(punishment, PunishmentStatus.valueOf(punishment.getStatus().name()), punishment.getRevokedReason());
    }

    /**
     * The punishment as it is stored once revoked with this reason (the object itself is not changed).
     */
    public static PunishmentView revoked(Punishment punishment, String reason) {
        return punishment(punishment, PunishmentStatus.REVOKED, Optional.ofNullable(PunishService.storedRevokeReason(reason)));
    }

    private static PunishmentView punishment(Punishment punishment, PunishmentStatus status, Optional<String> revokedReason) {
        return new PunishmentView(
                punishment.getId(),
                PunishmentType.valueOf(punishment.getType().name()),
                punishment.getNickname(),
                punishment.getPlayerUUID(),
                punishment.getIpAddress(),
                punishment.getPunisher(),
                punishment.getReason().orElse(""),
                instant(punishment.getStartedAt()),
                punishment.getFinishAt().map(ApiViews::instant),
                status,
                revokedReason.filter(reason -> !reason.isBlank()),
                punishment.getEvidences() == null ? List.of() : punishment.getEvidences(),
                punishment.isPermanent(),
                punishment.isSoft(),
                punishment.isOnlineOnly(),
                Optional.ofNullable(punishment.getPausedRemaining()).map(Duration::ofMillis),
                punishment.getScope());
    }

    public static WarnView warn(Warn warn) {
        return new WarnView(
                warn.getId(),
                warn.getNickname(),
                warn.getPlayerUUID(),
                warn.getPunisher(),
                warn.getReason() == null ? "" : warn.getReason(),
                instant(warn.getStartedAt()),
                warn.getFinishAt().map(ApiViews::instant),
                WarnStatus.valueOf(warn.getStatus().name()));
    }

    static Instant instant(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }

}
