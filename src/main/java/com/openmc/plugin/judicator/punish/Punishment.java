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
    private PunishStatus status;
    private List<String> evidences;
    private PunishType type;
    private boolean permanent;

    public Punishment(Long id, UUID playerUUID, String reason, String punisher, String nickname, String ipAddress, LocalDateTime startedAt, LocalDateTime finishAt, PunishStatus status, List<String> evidences, PunishType type, boolean permanent) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.reason = reason;
        this.punisher = punisher;
        this.nickname = nickname;
        this.ipAddress = ipAddress;
        this.startedAt = startedAt;
        this.finishAt = finishAt;
        this.status = status;
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
                PunishStatus.ACTIVE,
                evidences,
                type,
                !type.isTemp());
    }

    public Optional<LocalDateTime> getFinishAt() {
        return Optional.ofNullable(finishAt);
    }

    public Optional<String> getIpAddress() {
        return Optional.ofNullable(ipAddress);
    }

}
