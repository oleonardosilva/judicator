package com.elless.judicator.importer;

import com.elless.judicator.punish.Punishment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LiteBans tables {@code <prefix>bans}, {@code <prefix>mutes}, {@code <prefix>warnings} and {@code <prefix>history}.
 * <p>
 * Columns used: {@code uuid}, {@code ip}, {@code reason}, {@code banned_by_uuid}, {@code banned_by_name},
 * {@code time} and {@code until} (epoch milliseconds; {@code until <= 0} is permanent), {@code active},
 * {@code ipban}, {@code ipban_wildcard} and {@code server_scope} when present; history: {@code uuid}, {@code name},
 * {@code date}. Kicks are not imported (Judicator does not store kicks).
 */
final class LiteBansImporter implements ImportSource {

    private static final String OFFLINE = "#offline#";

    @Override
    public String name() {
        return "litebans";
    }

    @Override
    public ImportResult read(ImportContext context) throws Exception {
        final String prefix = context.config(name()).node("table-prefix").getString("litebans_");
        if (!prefix.matches("[A-Za-z0-9_]*")) throw new ImportException("table-prefix may only contain letters, digits and _.");

        final ImportResult result = new ImportResult();
        try (SourceConnection source = SourceConnection.open(context.config(name()), context.judicator().getPlatform().getDataDirectory())) {
            final NameHistory names = names(source, prefix);
            readPunishments(context, source, prefix + "bans", true, names, result);
            readPunishments(context, source, prefix + "mutes", false, names, result);
            readWarnings(context, source, prefix + "warnings", names, result);
        }
        return result;
    }

    /**
     * LiteBans keeps the player's name only in the history table ({@code uuid}, {@code name}, {@code date}).
     */
    private NameHistory names(SourceConnection source, String prefix) throws SQLException, ImportException {
        final NameHistory names = new NameHistory();
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(prefix + "history"));
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                final UUID uuid = parse(rs.getString("uuid"));
                final java.sql.Timestamp date = rs.getTimestamp("date");
                names.add(uuid, rs.getString("name"), date == null ? 0L : date.getTime());
            }
        }
        return names;
    }

    private static UUID parse(String text) {
        if (text == null || text.equals(OFFLINE)) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void readPunishments(ImportContext context, SourceConnection source, String table, boolean ban,
                                 NameHistory names, ImportResult result) throws SQLException, ImportException {
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(table));
             ResultSet rs = statement.executeQuery()) {
            final boolean hasScope = SourceConnection.hasColumn(rs, "server_scope");
            final boolean hasWildcard = SourceConnection.hasColumn(rs, "ipban_wildcard");
            while (rs.next()) {
                if (hasWildcard && rs.getBoolean("ipban_wildcard")) {
                    result.skip("wildcard IP ban");
                    continue;
                }
                final String scope = hasScope && !globalScope(rs.getString("server_scope")) ? rs.getString("server_scope") : null;
                final String uuidText = rs.getString("uuid");
                UUID uuid = null;
                if (uuidText != null && !uuidText.equals(OFFLINE)) {
                    try {
                        uuid = UUID.fromString(uuidText);
                    } catch (IllegalArgumentException e) {
                        result.skip("invalid UUID");
                        continue;
                    }
                }
                String nickname = names.victimName(uuid);
                String address = null;
                if (rs.getBoolean("ipban")) {
                    address = rs.getString("ip");
                    if (address == null || address.equals(OFFLINE) || address.equals("#") || !ImportContext.validAddress(address)) {
                        result.skip("IP ban without a valid IP address");
                        continue;
                    }
                    // /banip <address> in LiteBans has no player: store it by address only.
                    if (!ImportContext.validName(nickname)) nickname = ImportContext.nameForAddress(address);
                } else {
                    if (uuid == null) {
                        result.skip("no player UUID (#offline#)");
                        continue;
                    }
                    if (!ImportContext.validName(nickname)) {
                        result.skip("player name unknown (not in LiteBans history)");
                        continue;
                    }
                }

                final long until = rs.getLong("until");
                final boolean permanent = until <= 0;
                final LocalDateTime start = ImportContext.fromEpochMillis(rs.getLong("time"));
                final LocalDateTime end = permanent ? null : ImportContext.fromEpochMillis(until);
                final Punishment punishment = context.punishment(uuid, nickname, address, rs.getString("reason"),
                        operator(rs), ban, start, end, permanent, rs.getBoolean("active"), "LiteBans");
                // server_scope names a server (or a group Judicator also has in scopes.groups)
                final boolean group = scope != null && context.judicator().getScopeService().hasGroup(scope);
                if (context.scope(punishment, scope, group, result)) result.add(punishment);
            }
        }
    }

    private void readWarnings(ImportContext context, SourceConnection source, String table,
                              NameHistory names, ImportResult result) throws SQLException, ImportException {
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(table));
             ResultSet rs = statement.executeQuery()) {
            final boolean hasScope = SourceConnection.hasColumn(rs, "server_scope");
            while (rs.next()) {
                if (hasScope && !globalScope(rs.getString("server_scope"))) {
                    result.skip("warning limited to one server (server_scope; Judicator warns apply everywhere)");
                    continue;
                }
                final String uuidText = rs.getString("uuid");
                final UUID uuid = parse(uuidText);
                final String nickname = names.victimName(uuid);
                if (uuid == null || !ImportContext.validName(nickname)) {
                    result.skip("warning without player UUID or name");
                    continue;
                }
                final long until = rs.getLong("until");
                final boolean permanent = until <= 0;
                result.add(context.warn(uuid, nickname, rs.getString("reason"), operator(rs),
                        ImportContext.fromEpochMillis(rs.getLong("time")),
                        permanent ? null : ImportContext.fromEpochMillis(until), permanent, rs.getBoolean("active")));
            }
        }
    }

    private static boolean globalScope(String scope) {
        return scope == null || scope.isBlank() || scope.equals("*");
    }

    private static String operator(ResultSet rs) throws SQLException {
        final String operatorUuid = rs.getString("banned_by_uuid");
        if (operatorUuid != null && operatorUuid.equalsIgnoreCase("CONSOLE")) return ImportContext.CONSOLE;
        return rs.getString("banned_by_name");
    }
}
