package com.elless.judicator.importer;

import com.elless.judicator.punish.Punishment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LibertyBans 1.x schema (documented in its "The Database Schema" page and migrations):
 * <ul>
 *     <li>{@code <prefix>simple_history}: every punishment, with {@code id}, {@code type} (0 ban, 1 mute, 2 warn,
 *     3 kick), {@code victim_type} (0 player UUID, 1 IP address, 2 UUID + IP), {@code victim_uuid},
 *     {@code victim_address} (4 or 16 bytes), {@code operator} (all-zero UUID = console), {@code reason},
 *     {@code scope} ('' = every server), {@code start} and {@code end} (unix seconds, {@code end = 0} permanent);</li>
 *     <li>{@code <prefix>bans}, {@code <prefix>mutes}, {@code <prefix>warns}: ids of the punishments in force;</li>
 *     <li>{@code <prefix>names}: {@code uuid}, {@code name}, {@code updated}.</li>
 * </ul>
 * Bans and mutes for some servers keep their scope: a server ({@code scope_type} 1) as that server, a category (2) as
 * the group of the same name, which {@code scopes.groups} must define. Kicks and warns for some servers are not imported
 * (Judicator warns apply everywhere).
 */
final class LibertyBansImporter implements ImportSource {

    private static final UUID CONSOLE = new UUID(0L, 0L);

    @Override
    public String name() {
        return "libertybans";
    }

    @Override
    public ImportResult read(ImportContext context) throws Exception {
        final String prefix = context.config(name()).node("table-prefix").getString("libertybans_");
        if (!prefix.matches("[A-Za-z0-9_]*")) throw new ImportException("table-prefix may only contain letters, digits and _.");

        final ImportResult result = new ImportResult();
        try (SourceConnection source = SourceConnection.open(context.config(name()), context.judicator().getPlatform().getDataDirectory())) {
            final NameHistory names = names(source, prefix);
            final Set<Long> active = new HashSet<>();
            for (String table : new String[]{"bans", "mutes", "warns"}) {
                try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(prefix + table));
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) active.add(rs.getLong("id"));
                }
            }
            try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(prefix + "simple_history"));
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    map(context, rs, names, active, result);
                }
            }
        }
        return result;
    }

    private static NameHistory names(SourceConnection source, String prefix) throws SQLException, ImportException {
        final NameHistory names = new NameHistory();
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(prefix + "names"));
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                names.add(SourceConnection.uuid(rs, "uuid"), rs.getString("name"), rs.getLong("updated"));
            }
        }
        return names;
    }

    private static void map(ImportContext context, ResultSet rs, NameHistory names, Set<Long> active,
                            ImportResult result) throws SQLException {
        final int type = rs.getInt("type");
        if (type == 3) {
            result.skip("kick (not stored by Judicator)");
            return;
        }
        if (type < 0 || type > 3) {
            result.skip("unknown punishment type " + type);
            return;
        }
        // scope_type exists since server scopes were added (0 = every server, 1 = a server, 2 = a category); before that
        // the scope was a server name, empty for every server.
        final int scopeType = SourceConnection.hasColumn(rs, "scope_type") ? rs.getInt("scope_type")
                : rs.getString("scope") == null || rs.getString("scope").isBlank() ? 0 : 1;
        if (scopeType < 0 || scopeType > 2) {
            result.skip("unknown scope type " + scopeType);
            return;
        }
        final String scope = scopeType == 0 ? null : rs.getString("scope");
        if (type == 2 && scope != null) {
            result.skip("warn limited to some servers (Judicator warns apply everywhere)");
            return;
        }

        final int victimType = rs.getInt("victim_type");
        if (victimType < 0 || victimType > 2) {
            result.skip("unknown victim type " + victimType);
            return;
        }
        // Type 0: player; 1: IP address; 2: player and IP address together.
        final UUID uuid = victimType == 1 ? null : SourceConnection.uuid(rs, "victim_uuid");
        final String address = victimType == 0 ? null : SourceConnection.address(rs, "victim_address");
        String nickname = names.victimName(uuid);
        if (victimType == 0) {
            if (uuid == null || !ImportContext.validName(nickname)) {
                result.skip("player name unknown (not in LibertyBans names)");
                return;
            }
        } else {
            if (!ImportContext.validAddress(address)) {
                result.skip("IP punishment without a valid address");
                return;
            }
            // Keep the UUID of a composite victim (enforced at login by UUID) even when its name is unknown.
            if (!ImportContext.validName(nickname)) nickname = ImportContext.nameForAddress(address);
        }

        final UUID operatorUuid = SourceConnection.uuid(rs, "operator");
        final String operator = operatorUuid == null || operatorUuid.equals(CONSOLE) ? ImportContext.CONSOLE
                : names.latestName(operatorUuid);

        final long end = rs.getLong("end");
        final boolean permanent = end == 0L;
        final LocalDateTime startedAt = ImportContext.fromEpochMillis(rs.getLong("start") * 1000L);
        final LocalDateTime finishAt = permanent ? null : ImportContext.fromEpochMillis(end * 1000L);
        final boolean isActive = active.contains(rs.getLong("id"));

        if (type == 2) {
            if (uuid == null) {
                result.skip("warn on an IP address (Judicator warns players)");
                return;
            }
            result.add(context.warn(uuid, nickname, rs.getString("reason"), operator, startedAt, finishAt, permanent, isActive));
            return;
        }
        final Punishment punishment = context.punishment(uuid, nickname, address, rs.getString("reason"), operator,
                type == 0, startedAt, finishAt, permanent, isActive, "LibertyBans");
        if (context.scope(punishment, scope, scopeType == 2, result)) result.add(punishment);
    }
}
