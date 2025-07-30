package com.openmc.judicator.punish.types;

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
    VIEW("judicator.view"),
    HISTORY("judicator.history"),
    KICK("judicator.kick"),
    WARN("judicator.warn"),
    TEMPWARN("judicator.tempwarn"),
    UNWARN("judicator.unwarn");

    private final String permission;

    PunishPermissions(String permission) {
        this.permission = permission;
    }

}
