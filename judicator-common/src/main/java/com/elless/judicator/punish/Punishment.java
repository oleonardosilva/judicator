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

    public PunishStatus getStatus() {
        final LocalDateTime now = LocalDateTime.now();
        if (revoked) return PunishStatus.REVOKED;
        if (type == PunishType.KICK) return PunishStatus.FINISHED;
        // a paused online-only mute has no end yet: it is still in force
        if (type.isTemp() && finishAt != null && finishAt.isBefore(now))
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

    public Optional<UUID> getPunisherUuid() {
        return Optional.ofNullable(punisherUuid);
    }

}
