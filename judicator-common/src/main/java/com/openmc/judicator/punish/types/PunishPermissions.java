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
    UNWARN("judicator.unwarn"),
    UNBAN("judicator.unban"),
    UNMUTE("judicator.unmute"),
    ALTS("judicator.alts"),
    BLAME("judicator.blame"),
    ALTS_NOTIFY("judicator.alts.notify"),
    SILENT("judicator.silent"),
    NOTIFY("judicator.notify"),
    NOTIFY_SILENT("judicator.notify.silent");

    public static final String LEVEL_PREFIX = "judicator.level.";

    private final String permission;

    PunishPermissions(String permission) {
        this.permission = permission;
    }

}
