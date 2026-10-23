package com.openmc.judicator.warns;

import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.platform.JudicatorPlayer;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@Getter
public class WarnBuilder {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private UUID playerUUID;
    private String reason, punisher = "Console", nickname;
    private LocalDateTime finishAt;
    private boolean silent;

    public WarnBuilder punisher(JudicatorPlayer player) {
        this.punisher = player.getName();
        return this;
    }

    /**
     * An author that is not a player online here (a plugin through the API).
     */
    public WarnBuilder punisher(String name) {
        this.punisher = name;
        return this;
    }

    public WarnBuilder duration(Duration duration) {
        this.finishAt = startedAt.plus(duration);
        return this;
    }

    public WarnBuilder silent(boolean silent) {
        this.silent = silent;
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

    public WarnBuilder target(JudicatorPlayer player) {
        this.playerUUID = player.getUniqueId();
        this.nickname = player.getName();
        return this;
    }

    public WarnBuilder target(String nickname, UUID  uuid) {
        this.nickname = nickname;
        this.playerUUID = uuid;
        return this;
    }

    public Warn build() {
        // warns.player_uuid is NOT NULL: a warn for a player whose UUID could not be found (never joined, Mojang
        // unreachable) would be rejected by the database and silently lost. Use the UUID an offline-mode server
        // gives that name; warns are looked up by name anyway.
        final UUID uuid = playerUUID != null ? playerUUID
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));
        final Warn warn = Warn.create(
                uuid,
                reason,
                punisher,
                nickname,
                startedAt,
                finishAt
        );
        warn.setSilent(silent);
        return warn;
    }

}
