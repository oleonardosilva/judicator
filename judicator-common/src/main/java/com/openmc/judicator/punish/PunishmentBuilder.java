package com.openmc.judicator.punish;

import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.platform.JudicatorPlayer;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoArgsConstructor
@Getter
public class PunishmentBuilder {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private final List<String> evidences = new ArrayList<>();
    private UUID playerUUID;
    /** the staff member's UUID; null for the console and plugins */
    private UUID punisherUuid;
    private String reason, punisher = "Console", nickname, ipAddress;
    private LocalDateTime finishAt;
    private PunishType type;
    private boolean silent;
    private boolean soft;
    private boolean onlineOnly;
    /** null: everywhere */
    private String scope;
    private JudicatorPlayer staff;

    /**
     * @return the staff member who applies it; empty for the console
     */
    public Optional<JudicatorPlayer> getStaff() {
        return Optional.ofNullable(staff);
    }

    public PunishmentBuilder punisher(JudicatorPlayer player) {
        this.staff = player;
        this.punisher = player.getName();
        this.punisherUuid = player.getUniqueId();
        return this;
    }

    /**
     * An author that is not a player online here (a plugin through the API): treated as the console by the rules.
     */
    public PunishmentBuilder punisher(String name) {
        this.staff = null;
        this.punisher = name;
        this.punisherUuid = null;
        return this;
    }

    public PunishmentBuilder silent(boolean silent) {
        this.silent = silent;
        return this;
    }

    public PunishmentBuilder soft(boolean soft) {
        this.soft = soft;
        return this;
    }

    public PunishmentBuilder onlineOnly(boolean onlineOnly) {
        this.onlineOnly = onlineOnly;
        return this;
    }

    /**
     * @param scope as {@link com.openmc.judicator.punish.scopes.ScopeService#parse} gives it; null for everywhere
     */
    public PunishmentBuilder scope(String scope) {
        this.scope = scope;
        return this;
    }

    public PunishmentBuilder type(PunishType type) {
        this.type = type;
        return this;
    }

    public PunishmentBuilder reason(String reason) {
        if (reason != null) {
            this.reason = reason;
        }
        return this;
    }

    public PunishmentBuilder duration(String input) {
        this.finishAt = DateTimeOffsetParser.getFinishedAtFromDuration(input, startedAt);
        return this;
    }

    public PunishmentBuilder duration(Duration duration) {
        this.finishAt = startedAt.plus(duration);
        return this;
    }

    public PunishmentBuilder target(JudicatorPlayer player) {
        this.playerUUID = player.getUniqueId();
        this.nickname = player.getName();
        return this;
    }

    public PunishmentBuilder ipAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public PunishmentBuilder target(PlayerRef player) {
        this.playerUUID = player.uuid();
        this.nickname = player.name();
        return this;
    }

    public PunishmentBuilder target(String nickname, UUID uuid) {
        this.playerUUID = uuid;
        this.nickname = nickname;
        return this;
    }


    public PunishmentBuilder appendEvidences(String evidence) {
        this.evidences.addAll(Arrays.asList(evidence.split(" ")));
        return this;
    }

    public PunishmentBuilder evidences(List<String> links) {
        this.evidences.addAll(links);
        return this;
    }

    public boolean isBan() {
        return this.type == PunishType.BAN || this.type == PunishType.TEMPBAN;
    }

    public boolean isMute() {
        return this.type == PunishType.MUTE || this.type == PunishType.TEMPMUTE;
    }

    public Punishment build() {
        final Punishment punishment = Punishment.create(
                playerUUID,
                reason,
                punisher,
                nickname,
                ipAddress,
                startedAt,
                finishAt,
                evidences,
                type
        );
        punishment.setPunisherUuid(punisherUuid);
        punishment.setSilent(silent);
        punishment.setSoft(soft);
        punishment.setOnlineOnly(onlineOnly);
        // a kick is over at once: it has no servers to apply on
        punishment.setScope(isBan() || isMute() ? scope : null);
        return punishment;
    }

}
