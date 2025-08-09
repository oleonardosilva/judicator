package com.openmc.judicator.warns;

import com.openmc.judicator.warns.types.WarnStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Setter
@NoArgsConstructor
@Getter
public class Warn {

    private Long id;
    private UUID playerUUID;
    private String reason, punisher, nickname;
    private LocalDateTime startedAt, finishAt;
    private boolean permanent;

    public Warn(Long id, UUID playerUUID, String reason, String punisher, String nickname, LocalDateTime startedAt, LocalDateTime finishAt, boolean permanent) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.reason = reason;
        this.punisher = punisher;
        this.nickname = nickname;
        this.startedAt = startedAt;
        this.finishAt = finishAt;
        this.permanent = permanent;
    }

    public static Warn create(UUID playerUUID, String reason, String punisher, String nickname,
                              LocalDateTime startedAt, LocalDateTime finishAt) {
        return new Warn(null,
                playerUUID,
                reason,
                punisher,
                nickname,
                startedAt,
                finishAt,
                finishAt == null);
    }

    public WarnStatus getStatus() {
        final LocalDateTime now = LocalDateTime.now();
        if (!isPermanent() && finishAt.isBefore(now))
            return WarnStatus.FINISHED;
        return WarnStatus.ACTIVE;
    }

    public Optional<LocalDateTime> getFinishAt() {
        return Optional.ofNullable(finishAt);
    }

    public String getSafeNickname() {
        return getNickname().replaceAll("[^a-zA-Z0-9_]", "");
    }

}
