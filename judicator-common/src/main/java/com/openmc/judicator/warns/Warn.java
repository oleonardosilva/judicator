package com.openmc.judicator.warns;

import com.openmc.judicator.players.PlayerRef;
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
    /**
     * The UUID of the staff member who applied it when it was a player; null for the console, a plugin or a record older than 2.0.
     */
    private UUID punisherUuid;
    private LocalDateTime startedAt, finishAt;
    private boolean permanent;
    private transient boolean silent;

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
    
    public Optional<UUID> getPlayerUUID() {
        return Optional.ofNullable(this.playerUUID);
    }

    public Optional<LocalDateTime> getFinishAt() {
        return Optional.ofNullable(finishAt);
    }

    public String getSafeNickname() {
        return getNickname().replaceAll("[^a-zA-Z0-9_]", "");
    }

    public Optional<UUID> getPunisherUuid() {
        return Optional.ofNullable(punisherUuid);
    }

    /**
     * The UUID of the account, or empty when the warn was stored for a name nobody had seen: {@code warns.player_uuid}
     * is NOT NULL, so such a warn holds the offline-mode UUID of the name instead.
     */
    public Optional<UUID> getAccountUuid() {
        if (playerUUID == null || nickname == null || playerUUID.equals(PlayerRef.offlineUuid(nickname))) return Optional.empty();
        return Optional.of(playerUUID);
    }

}
