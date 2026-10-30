package com.elless.judicator.importer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * AdvancedBan tables {@code PunishmentHistory} (every punishment ever applied) and {@code Punishments} (the ones
 * still in force), both with {@code name}, {@code uuid}, {@code reason}, {@code operator}, {@code punishmentType},
 * {@code start} and {@code end} (epoch milliseconds, {@code -1} = permanent).
 * <p>
 * {@code uuid} holds the IP address for IP_BAN/TEMP_IP_BAN, a UUID without dashes for players, or the name on
 * servers without UUIDs. A history row that is no longer in {@code Punishments} was removed. KICK and NOTE are not
 * imported.
 */
final class AdvancedBanImporter implements ImportSource {

    @Override
    public String name() {
        return "advancedban";
    }

    @Override
    public ImportResult read(ImportContext context) throws Exception {
        final ImportResult result = new ImportResult();
        try (SourceConnection source = SourceConnection.open(context.config(name()), context.judicator().getPlatform().getDataDirectory())) {
            final Set<String> active = activeKeys(source);
            final Set<String> seen = new HashSet<>();
            try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table("PunishmentHistory"));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    seen.add(key(rs));
                    map(context, rs, active, result);
                }
            }
            // A punishment still in force whose history row is gone (history cleaned by hand) must not be lost.
            try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table("Punishments"));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (!seen.contains(key(rs))) map(context, rs, active, result);
                }
            }
        }
        return result;
    }

    /**
     * AdvancedBan identifies a punishment by uuid + start + punishmentType.
     */
    private static Set<String> activeKeys(SourceConnection source) throws SQLException, ImportException {
        final Set<String> keys = new HashSet<>();
        // SELECT *: "start" and "end" are reserved words in HSQLDB, AdvancedBan's default storage.
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table("Punishments"));
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                keys.add(key(rs));
            }
        }
        return keys;
    }

    private static String key(ResultSet rs) throws SQLException {
        return key(rs.getString("uuid"), rs.getLong("start"), rs.getString("punishmentType"));
    }

    private static String key(String uuid, long start, String type) {
        return (uuid == null ? "" : uuid.toLowerCase(Locale.ROOT)) + "|" + start + "|" + type;
    }

    private static void map(ImportContext context, ResultSet rs, Set<String> active, ImportResult result) throws SQLException {
        final String type = rs.getString("punishmentType");
        if (type == null) {
            result.skip("no punishment type");
            return;
        }
        final boolean ban;
        final boolean warning;
        final boolean ipBased = type.equals("IP_BAN") || type.equals("TEMP_IP_BAN");
        switch (type) {
            case "BAN", "TEMP_BAN", "IP_BAN", "TEMP_IP_BAN" -> {
                ban = true;
                warning = false;
            }
            case "MUTE", "TEMP_MUTE" -> {
                ban = false;
                warning = false;
            }
            case "WARNING", "TEMP_WARNING" -> {
                ban = false;
                warning = true;
            }
            default -> {
                result.skip("type " + type + " (not stored by Judicator)");
                return;
            }
        }

        final String victim = rs.getString("uuid");
        String name = rs.getString("name");
        final long start = rs.getLong("start");
        final long end = rs.getLong("end");
        if (victim == null || start == 0L || end == 0L) {
            result.skip("incomplete row (uuid, start or end missing)");
            return;
        }
        final boolean permanent = end == -1L;
        final LocalDateTime startedAt = ImportContext.fromEpochMillis(start);
        final LocalDateTime finishAt = permanent ? null : ImportContext.fromEpochMillis(end);
        final boolean isActive = active.contains(key(victim, start, type));

        String address = null;
        UUID uuid = null;
        if (ipBased) {
            if (!ImportContext.validAddress(victim)) {
                result.skip("IP ban without a valid IP address");
                return;
            }
            address = victim;
            if (ImportContext.validName(name) && !name.equalsIgnoreCase(victim)) {
                // Banned through a player's name, like Judicator's own /banip: keep that player.
                uuid = context.uuidForName(name).orElse(null);
            } else {
                name = ImportContext.nameForAddress(victim);
            }
        } else {
            if (!ImportContext.validName(name)) {
                result.skip("player name unknown or too long");
                return;
            }
            uuid = victim.length() == 32 ? context.judicator().getUuidManager().fromString(victim) : null;
            if (uuid == null) uuid = context.uuidForName(name).orElse(null);
        }

        if (warning) {
            if (uuid == null) {
                result.skip("warning without UUID (online-mode server, name only)");
                return;
            }
            result.add(context.warn(uuid, name, rs.getString("reason"), rs.getString("operator"),
                    startedAt, finishAt, permanent, isActive));
            return;
        }
        result.add(context.punishment(uuid, name, address, rs.getString("reason"), rs.getString("operator"),
                ban, startedAt, finishAt, permanent, isActive, "AdvancedBan"));
    }
}
