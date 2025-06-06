package com.openmc.plugin.judicator.punish.types;

import lombok.Getter;

@Getter
public enum PunishPermissions {

    ADMIN("judicator.admin"),
    PUNISH("judicator.punish"),
    BAN("judicator.ban"),
    BANIP("judicator.ban.ip"),
    TEMPBANIP("judicator.tempban.ip"),
    TEMPBAN("judicator.tempban"),
    MUTE("judicator.mute"),
    MUTEIP("judicator.mute.ip"),
    TEMPMUTEIP("judicator.tempmute.ip"),
    TEMPMUTE("judicator.tempmute"),
    UNBAN("judicator.unban"),
    UNMUTE("judicator.unmute"),
    UNPUNISH("judicator.unpunish"),
    PUNISHVIEW("judicator.view"),
    PUNISHHISTORY("judicator.history"),
    KICK("judicator.kick"),
    WARN("judicator.warn"),
    TEMPWARN("judicator.tempwarn"),
    UNWARN("judicator.unwarn"),
    WARNS("judicator.warns");

    private final String permission;

    PunishPermissions(String permission) {
        this.permission = permission;
    }

}
