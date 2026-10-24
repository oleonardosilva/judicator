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
    BANLIST("judicator.banlist"),
    MUTELIST("judicator.mutelist"),
    ACTIVITY("judicator.activity"),
    STAFFROLLBACK("judicator.staffrollback"),
    EXTEND("judicator.extend"),
    CHANGEREASON("judicator.changereason"),
    NOTE("judicator.note"),
    NOTES("judicator.notes"),
    DELNOTE("judicator.delnote"),
    NOTES_NOTIFY("judicator.notes.notify"),
    INFO("judicator.info"),
    SOFTMUTE("judicator.softmute"),
    TEMPSOFTMUTE("judicator.tempsoftmute"),
    ALTS_NOTIFY("judicator.alts.notify"),
    SILENT("judicator.silent"),
    NOTIFY("judicator.notify"),
    NOTIFY_SILENT("judicator.notify.silent"),
    REPORT("judicator.report"),
    REPORT_OFFLINE("judicator.report.offline"),
    REPORTS("judicator.reports"),
    REPORTS_MANAGE("judicator.reports.manage"),
    REPORTS_TP("judicator.reports.tp"),
    REPORTS_NOTIFY("judicator.reports.notify"),
    MENU("judicator.menu");

    public static final String LEVEL_PREFIX = "judicator.level.";

    private final String permission;

    PunishPermissions(String permission) {
        this.permission = permission;
    }

}
