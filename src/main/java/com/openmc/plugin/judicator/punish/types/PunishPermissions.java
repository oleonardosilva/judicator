package com.openmc.plugin.judicator.punish.types;

import lombok.Getter;

@Getter
public enum PunishPermissions {

    ADMIN("punishments.admin"),
    PUNISH("punishments.punish"),
    BAN("punishments.ban"),
    BANIP("punishments.ban.ip"),
    TEMPBANIP("punishments.tempban.ip"),
    TEMPBAN("punishments.tempban"),
    MUTE("punishments.mute"),
    MUTEIP("punishments.mute.ip"),
    TEMPMUTEIP("punishments.tempmute.ip"),
    TEMPMUTE("punishments.tempmute"),
    UNBAN("punishments.unban"),
    UNMUTE("punishments.unmute"),
    UNPUNISH("punishments.unpunish"),
    PUNISHVIEW("punishments.view"),
    PUNISHHISTORY("punishments.history"),
    KICK("punishments.kick"),
    WARN("punishments.warn"),
    TEMPWARN("punishments.tempwarn"),
    UNWARN("punishments.unwarn"),
    WARNS("punishments.warns"),
    CHECK("punishments.check");

    private final String permission;

    PunishPermissions(String permission) {
        this.permission = permission;
    }

}
