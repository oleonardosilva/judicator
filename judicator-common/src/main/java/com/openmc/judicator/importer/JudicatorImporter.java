package com.openmc.judicator.importer;

import com.openmc.judicator.commons.db.LegacyTables;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.punish.PlayerAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.warns.Warn;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Another Judicator database (e.g. the SQLite file of a server moving to MySQL), of version 1.x (tables without a
 * prefix) or 2.x ({@code table-prefix}): {@code punishments} with their
 * revocation, evidence and kicks, {@code warns}, staff {@code notes} and the {@code access_addresses} used by {@code /alts} and offline IP
 * punishments. Staff levels are not copied: they are recorded again when each staff member joins.
 */
final class JudicatorImporter implements ImportSource {

    @Override
    public String name() {
        return "judicator";
    }

    @Override
    public ImportResult read(ImportContext context) throws Exception {
        final String configured = context.config(name()).node("table-prefix").getString(RelationalDBManager.DEFAULT_TABLE_PREFIX);
        final String prefix = RelationalDBManager.normalizePrefix(configured).orElseThrow(() -> new ImportException(
                "table-prefix '" + configured + "' is not valid: a letter followed by up to 15 letters, digits and _ (or '')."));
        final ImportResult result = new ImportResult();
        try (SourceConnection source = SourceConnection.open(context.config(name()), context.judicator().getPlatform().getDataDirectory())) {
            final String punishments = findTable(source, prefix, "punishments").orElseThrow(() -> new ImportException(
                    "Table " + prefix + "punishments not found in the source database (check jdbc-url and table-prefix in import.yml)."));
            try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + punishments);
                 ResultSet rs = statement.executeQuery()) {
                final Set<String> columns = columns(rs);
                while (rs.next()) punishment(rs, columns, result);
            }
            final Optional<String> warns = findTable(source, prefix, "warns");
            if (warns.isPresent()) {
                try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + warns.get());
                     ResultSet rs = statement.executeQuery()) {
                    final Set<String> columns = columns(rs);
                    while (rs.next()) warn(rs, columns, result);
                }
            }
            final Optional<String> notes = findTable(source, prefix, "notes");
            if (notes.isPresent()) {
                try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + notes.get());
                     ResultSet rs = statement.executeQuery()) {
                    final Set<String> columns = columns(rs);
                    while (rs.next()) note(rs, columns, result);
                }
            }
            final Optional<String> addresses = source.findTable(prefix + "player_addresses");
            if (addresses.isPresent()) {
                try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + addresses.get());
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) playerAddress(rs, result);
                }
            } else {
                // 1.x: one row per address with the names of its accounts
                final Optional<String> legacy = findTable(source, prefix, "access_addresses");
                if (legacy.isPresent()) {
                    try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + legacy.get());
                         ResultSet rs = statement.executeQuery()) {
                        final Function<String, UUID> legacyNames = context.judicator().getPlayerDirectory().legacyNames();
                        while (rs.next()) address(rs, legacyNames, result);
                    }
                }
            }
        }
        return result;
    }

    /**
     * The table with the prefix (2.x) or, for a database of 1.x, without it — then only when it has the columns of
     * Judicator 1.x, as another plugin in the same database may have a table with that name.
     */
    private static Optional<String> findTable(SourceConnection source, String prefix, String name) throws SQLException {
        final Optional<String> prefixed = source.findTable(prefix + name);
        if (prefixed.isPresent() || prefix.isEmpty()) return prefixed;
        final Optional<String> legacy = source.findTable(name);
        return legacy.isPresent() && LegacyTables.hasColumnsOf(source.connection(), legacy.get(), name) ? legacy : Optional.empty();
    }

    /**
     * Column names of the result, lower-cased: a database of an older version lacks the newer ones.
     */
    private static Set<String> columns(ResultSet rs) throws SQLException {
        final ResultSetMetaData meta = rs.getMetaData();
        final Set<String> columns = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
        return columns;
    }

    private static void punishment(ResultSet rs, Set<String> columns, ImportResult result) throws SQLException {
        final PunishType type;
        try {
            type = PunishType.valueOf(String.valueOf(rs.getString("type")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            result.skip("unknown punishment type");
            return;
        }
        final String nickname = rs.getString("nickname");
        final LocalDateTime startedAt = seconds(SqlTime.read(rs, "started_at"));
        if (nickname == null || nickname.isBlank() || startedAt == null) {
            result.skip("punishment without a player name or start");
            return;
        }
        final String address = rs.getString("ip_address");
        final String evidences = rs.getString("evidences");
        final Punishment punishment = new Punishment(null, uuid(rs.getString("player_uuid")), rs.getString("reason"),
                rs.getString("punisher"), nickname, address == null || address.isBlank() ? null : address, startedAt,
                SqlTime.read(rs, "finish_at"), rs.getBoolean("revoked"), rs.getString("revoked_reason"),
                evidences == null ? new ArrayList<>() : new ArrayList<>(Arrays.stream(evidences.split(",")).filter(s -> !s.isBlank()).toList()),
                type, rs.getBoolean("permanent"));
        if (columns.contains("soft")) punishment.setSoft(rs.getBoolean("soft"));
        if (columns.contains("online_only")) punishment.setOnlineOnly(rs.getBoolean("online_only"));
        if (columns.contains("punisher_uuid")) punishment.setPunisherUuid(uuid(rs.getString("punisher_uuid")));
        if (columns.contains("paused_remaining")) {
            final long paused = rs.getLong("paused_remaining");
            punishment.setPausedRemaining(rs.wasNull() ? null : paused);
        }
        result.add(punishment);
    }

    private static void warn(ResultSet rs, Set<String> columns, ImportResult result) throws SQLException {
        final String nickname = rs.getString("nickname");
        final LocalDateTime startedAt = seconds(SqlTime.read(rs, "started_at"));
        if (nickname == null || nickname.isBlank() || startedAt == null) {
            result.skip("warn without a player name or start");
            return;
        }
        final Warn warn = new Warn(null, uuid(rs.getString("player_uuid")), rs.getString("reason"), rs.getString("punisher"),
                nickname, startedAt, SqlTime.read(rs, "finish_at"), rs.getBoolean("permanent"));
        if (columns.contains("punisher_uuid")) warn.setPunisherUuid(uuid(rs.getString("punisher_uuid")));
        result.add(warn);
    }

    private static void note(ResultSet rs, Set<String> columns, ImportResult result) throws SQLException {
        final String nickname = rs.getString("nickname");
        final String content = rs.getString("content");
        final LocalDateTime createdAt = seconds(SqlTime.read(rs, "created_at"));
        if (nickname == null || nickname.isBlank() || content == null || createdAt == null) {
            result.skip("note without a player name, text or date");
            return;
        }
        final String author = rs.getString("author");
        final Note note = new Note(null, uuid(rs.getString("player_uuid")), nickname, author == null ? "Console" : author, content, createdAt);
        if (columns.contains("author_uuid")) note.setAuthorUuid(uuid(rs.getString("author_uuid")));
        result.add(note);
    }

    private static void playerAddress(ResultSet rs, ImportResult result) throws SQLException {
        final String host = rs.getString("host_address");
        final String nickname = rs.getString("nickname");
        final LocalDateTime lastUsage = SqlTime.read(rs, "last_usage");
        if (host == null || host.isBlank() || nickname == null || nickname.isBlank() || lastUsage == null) {
            result.skip("address without IP, account or last use");
            return;
        }
        result.add(new PlayerAddress(null, uuid(rs.getString("player_uuid")), nickname, host, lastUsage));
    }

    private static void address(ResultSet rs, Function<String, UUID> legacyNames, ImportResult result) throws SQLException {
        final String host = rs.getString("host_address");
        final String accounts = rs.getString("accounts");
        final LocalDateTime lastUsage = SqlTime.read(rs, "last_usage");
        if (host == null || host.isBlank() || accounts == null || lastUsage == null) {
            result.skip("address without IP, accounts or last use");
            return;
        }
        final List<String> names = Arrays.stream(accounts.split(","))
                .filter(name -> !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (names.isEmpty()) {
            result.skip("address without accounts");
            return;
        }
        for (String name : names) {
            result.add(new PlayerAddress(null, legacyNames.apply(name), name, host, lastUsage));
        }
    }

    /**
     * Whole seconds, like the duplicate keys: a MySQL DATETIME rounds a fraction, so a row of 12:00:00.700 would be
     * stored as 12:00:01 and imported again on the next run.
     */
    private static LocalDateTime seconds(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.SECONDS);
    }

    private static UUID uuid(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
