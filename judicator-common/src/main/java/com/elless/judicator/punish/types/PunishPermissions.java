package com.elless.judicator.punish.types;

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
    STAFFUNLOCK("judicator.staffunlock"),
    /** not held to {@code staff-rate-limit}; only an explicit grant counts */
    RATELIMIT_BYPASS("judicator.ratelimit.bypass"),
    /** hears of a staff member locked by {@code staff-rate-limit} */
    RATELIMIT_NOTIFY("judicator.ratelimit.notify"),
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
    MENU("judicator.menu"),
    JAIL("judicator.jail"),
    TEMPJAIL("judicator.tempjail"),
    UNJAIL("judicator.unjail"),
    JAILLIST("judicator.jaillist"),
    /** makes and changes the jails ({@code /jails}) */
    JAILS("judicator.jails"),
    /** hits a fugitive to take them back to jail */
    JAIL_GUARD("judicator.jail.guard"),
    /** hears of escapes and catches */
    JAIL_NOTIFY("judicator.jail.notify");

    public static final String LEVEL_PREFIX = "judicator.level.";

    private final String permission;

    PunishPermissions(String permission) {
        this.permission = permission;
    }

}
