package com.openmc.judicator.punish;

import com.openmc.judicator.commons.DateTimeOffsetParser;
import com.openmc.judicator.punish.types.PunishType;
import com.velocitypowered.api.proxy.Player;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@Getter
public class PunishmentBuilder {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private final List<String> evidences = new ArrayList<>();
    private UUID playerUUID;
    private String reason, punisher = "Console", nickname, ipAddress;
    private LocalDateTime finishAt;
    private PunishType type;

    public PunishmentBuilder punisher(Player player) {
        this.punisher = player.getUsername();
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

    public PunishmentBuilder target(Player player) {
        this.playerUUID = player.getUniqueId();
        this.nickname = player.getUsername();
        return this;
    }

    public PunishmentBuilder ipAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public PunishmentBuilder target(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public PunishmentBuilder playerUUID(UUID uuid) {
        this.playerUUID = uuid;
        return this;
    }

    public PunishmentBuilder appendEvidences(String evidence) {
        this.evidences.addAll(Arrays.asList(evidence.split(" ")));
        return this;
    }

    public PunishmentBuilder appendEvidence(String evidence) {
        this.evidences.add(evidence);
        return this;
    }

    public boolean isBan() {
        return this.type == PunishType.BAN || this.type == PunishType.TEMPBAN;
    }

    public boolean isMute() {
        return this.type == PunishType.MUTE || this.type == PunishType.TEMPMUTE;
    }

    public Punishment build() {
        return Punishment.create(
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
    }

}
