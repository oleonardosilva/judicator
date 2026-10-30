package com.elless.judicator.warns;

import com.elless.judicator.commons.DateTimeOffsetParser;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.players.PlayerRef;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@Getter
public class WarnBuilder {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private UUID playerUUID;
    /** the staff member's UUID; null for the console and plugins */
    private UUID punisherUuid;
    private String reason, punisher = "Console", nickname;
    private LocalDateTime finishAt;
    private boolean silent;

    public WarnBuilder punisher(JudicatorPlayer player) {
        this.punisher = player.getName();
        this.punisherUuid = player.getUniqueId();
        return this;
    }

    /**
     * An author that is not a player online here (a plugin through the API).
     */
    public WarnBuilder punisher(String name) {
        this.punisher = name;
        this.punisherUuid = null;
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

    public WarnBuilder target(PlayerRef player) {
        this.nickname = player.name();
        this.playerUUID = player.uuid();
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
        // gives that name, which the lookups treat as "no UUID" (Warn#getAccountUuid) until the account joins.
        final UUID uuid = playerUUID != null ? playerUUID : PlayerRef.offlineUuid(nickname);
        final Warn warn = Warn.create(
                uuid,
                reason,
                punisher,
                nickname,
                startedAt,
                finishAt
        );
        warn.setPunisherUuid(punisherUuid);
        warn.setSilent(silent);
        return warn;
    }

}
