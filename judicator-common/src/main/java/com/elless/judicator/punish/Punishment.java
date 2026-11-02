package com.elless.judicator.punish;

import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Setter
@NoArgsConstructor
@Getter
public class Punishment {

    private Long id;
    private UUID playerUUID;
    private String reason, punisher, nickname, ipAddress;
    /**
     * The UUID of the staff member who applied it when it was a player; null for the console, a plugin or a record older than 2.0.
     */
    private UUID punisherUuid;
    private LocalDateTime startedAt, finishAt;
    private boolean revoked;
    private String revokedReason;
    /**
     * Who revoked it and when; null while it is in force and for a record revoked before they were stored (or
     * imported). The UUID only when a player revoked it.
     */
    private String revokedBy;
    private UUID revokedByUuid;
    private LocalDateTime revokedAt;
    private List<String> evidences;
    private PunishType type;
    private boolean permanent;
    private transient boolean silent;
    /**
     * Soft mute: the player still sees their own messages, nobody else does.
     */
    private boolean soft;
    /**
     * The time of this temporary mute only runs while the player is online.
     */
    private boolean onlineOnly;
    /**
     * Milliseconds left of an online-only mute while the player is offline (then {@code finishAt} is null); null
     * while it runs.
     */
    private Long pausedRemaining;
    /**
     * Where it applies: null everywhere, else {@code server:<name>} or {@code group:<name>}
     * ({@link com.elless.judicator.punish.scopes.ScopeService}).
     */
    private String scope;
    /**
     * The jail ({@link com.elless.judicator.jails.Jail}) of a {@code JAIL}/{@code TEMPJAIL}; null for the other types.
     */
    private String jail;
    /**
     * When the prisoner escaped; null while they are in jail. A fugitive's punishment ends when the guards had their
     * time to catch them ({@code finishAt}).
     */
    private LocalDateTime escapedAt;
    /**
     * Milliseconds of a temporary jail left when the prisoner escaped, given back if they are caught; null otherwise.
     */
    private Long jailLeft;

    public Punishment(Long id, UUID playerUUID, String reason, String punisher, String nickname, String ipAddress, LocalDateTime startedAt, LocalDateTime finishAt, boolean revoked, String revokedReason, List<String> evidences, PunishType type, boolean permanent) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.reason = reason;
        this.punisher = punisher;
        this.nickname = nickname;
        this.ipAddress = ipAddress;
        this.startedAt = startedAt;
        this.finishAt = finishAt;
        this.revoked = revoked;
        this.revokedReason = revokedReason;
        this.evidences = evidences == null ? new ArrayList<>() : evidences;
        this.type = type;
        this.permanent = permanent;
    }

    public static Punishment create(UUID playerUUID, String reason, String punisher, String nickname,
                                    String ipAddress,
                                    LocalDateTime startedAt, LocalDateTime finishAt,
                                    List<String> evidences, PunishType type) {
        return new Punishment(null,
                playerUUID,
                reason,
                punisher,
                nickname,
                ipAddress,
                startedAt,
                finishAt,
                false,
                null,
                evidences,
                type,
                !type.isTemp());
    }

    public boolean isBanType() {
        return this.type == PunishType.BAN || this.type == PunishType.TEMPBAN;
    }

    public boolean isMuteType() {
        return this.type == PunishType.MUTE || this.type == PunishType.TEMPMUTE;
    }

    public boolean isJailType() {
        return this.type == PunishType.JAIL || this.type == PunishType.TEMPJAIL;
    }

    /**
     * A jail whose prisoner escaped: they walk free while the guards may still catch them.
     */
    public boolean isEscaped() {
        return escapedAt != null;
    }

    public PunishStatus getStatus() {
        final LocalDateTime now = LocalDateTime.now();
        if (revoked) return PunishStatus.REVOKED;
        if (type == PunishType.KICK) return PunishStatus.FINISHED;
        // a paused online-only mute has no end yet: it is still in force; an escape gives a permanent jail an end
        if ((type.isTemp() || escapedAt != null) && finishAt != null && finishAt.isBefore(now))
            return PunishStatus.FINISHED;
        return PunishStatus.ACTIVE;
    }

    public boolean isPaused() {
        return pausedRemaining != null;
    }

    /**
     * The type as staff screens show it: {@code SOFTMUTE}/{@code TEMPSOFTMUTE} for soft mutes, else the type name.
     */
    public String getDisplayType() {
        if (!soft) return type.name();
        return type == PunishType.TEMPMUTE ? "TEMPSOFTMUTE" : "SOFTMUTE";
    }

    public Optional<UUID> getPlayerUUID() {
        return Optional.ofNullable(this.playerUUID);
    }

    public Optional<LocalDateTime> getFinishAt() {
        return Optional.ofNullable(finishAt);
    }

    public Optional<String> getIpAddress() {
        return Optional.ofNullable(ipAddress);
    }

    public Optional<String> getRevokedReason() {
        return Optional.ofNullable(revokedReason);
    }

    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> getScope() {
        return Optional.ofNullable(scope);
    }

    public Optional<String> getJail() {
        return Optional.ofNullable(jail);
    }

    public Optional<LocalDateTime> getEscapedAt() {
        return Optional.ofNullable(escapedAt);
    }

    public Optional<UUID> getPunisherUuid() {
        return Optional.ofNullable(punisherUuid);
    }

    public Optional<String> getRevokedBy() {
        return Optional.ofNullable(revokedBy);
    }

    public Optional<UUID> getRevokedByUuid() {
        return Optional.ofNullable(revokedByUuid);
    }

    public Optional<LocalDateTime> getRevokedAt() {
        return Optional.ofNullable(revokedAt);
    }

}
