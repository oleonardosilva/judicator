package com.openmc.plugin.judicator.punish;

import com.openmc.plugin.judicator.punish.types.PunishStatus;
import com.openmc.plugin.judicator.punish.types.PunishType;
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
    private LocalDateTime startedAt, finishAt;
    private boolean revoked;
    private String revokedReason;
    private List<String> evidences;
    private PunishType type;
    private boolean permanent;

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
                "",
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
        if (type.isTemp() && finishAt.isBefore(now))
            return PunishStatus.FINISHED;
        return revoked ? PunishStatus.REVOKED : PunishStatus.ACTIVE;
    }

    public Optional<LocalDateTime> getFinishAt() {
        return Optional.ofNullable(finishAt);
    }

    public Optional<String> getIpAddress() {
        return Optional.ofNullable(ipAddress);
    }

}
