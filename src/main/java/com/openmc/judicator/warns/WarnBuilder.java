package com.openmc.judicator.warns;

import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.velocitypowered.api.proxy.Player;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@Getter
public class WarnBuilder {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private UUID playerUUID;
    private String reason, punisher = "Console", nickname;
    private LocalDateTime finishAt;

    public WarnBuilder punisher(Player player) {
        this.punisher = player.getUsername();
        return this;
    }

    public WarnBuilder reason(String reason) {
        if (reason != null) {
            this.reason = reason;
        }
        return this;
    }

    public WarnBuilder duration(String input) {
        this.finishAt = DateTimeOffsetParser.getFinishedAtFromDuration(input, startedAt);
        return this;
    }

    public WarnBuilder target(Player player) {
        this.playerUUID = player.getUniqueId();
        this.nickname = player.getUsername();
        return this;
    }

    public WarnBuilder target(String nickname, UUID  uuid) {
        this.nickname = nickname;
        this.playerUUID = uuid;
        return this;
    }

    public Warn build() {
        return Warn.create(
                playerUUID,
                reason,
                punisher,
                nickname,
                startedAt,
                finishAt
        );
    }

}
