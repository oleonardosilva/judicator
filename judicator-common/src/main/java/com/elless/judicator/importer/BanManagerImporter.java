package com.elless.judicator.importer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * BanManager tables (default names {@code bm_*}, created by ORMLite from its data classes):
 * <ul>
 *     <li>{@code players}: {@code id} (UUID as BINARY(16)), {@code name}, {@code lastSeen};</li>
 *     <li>in force: {@code player_bans}, {@code player_mutes} ({@code player_id}), {@code ip_bans}, {@code ip_mutes}
 *     ({@code ip}, 4 or 16 bytes), with {@code reason}, {@code actor_id}, {@code created} and {@code expires}
 *     (unix seconds, {@code 0} = permanent). An online-only mute of an offline player has {@code expires = 0} and
 *     the time left in {@code pausedRemaining};</li>
 *     <li>removed or ended: the matching {@code *_records} tables, with the original {@code reason},
 *     {@code pastActor_id}, {@code pastCreated} and {@code expired}, plus {@code created} (when it was removed) and
 *     {@code createdReason};</li>
 *     <li>{@code player_warnings}: {@code player_id}, {@code reason}, {@code actor_id}, {@code created},
 *     {@code expires}.</li>
 * </ul>
 * Kicks, notes, name bans and IP range bans are not imported. Soft and online-only mutes become normal mutes.
 */
final class BanManagerImporter implements ImportSource {

    private static final String SOURCE = "BanManager";

    @Override
    public String name() {
        return "banmanager";
    }

    @Override
    public ImportResult read(ImportContext context) throws Exception {
        final String prefix = context.config(name()).node("table-prefix").getString("bm_");
        if (!prefix.matches("[A-Za-z0-9_]*")) throw new ImportException("table-prefix may only contain letters, digits and _.");

        final ImportResult result = new ImportResult();
        try (SourceConnection source = SourceConnection.open(context.config(name()), context.judicator().getPlatform().getDataDirectory())) {
            final NameHistory names = playerNames(source, prefix);
            readPunishments(context, source, prefix + "player_bans", true, false, true, names, result);
            readPunishments(context, source, prefix + "player_ban_records", true, false, false, names, result);
            readPunishments(context, source, prefix + "ip_bans", true, true, true, names, result);
            readPunishments(context, source, prefix + "ip_ban_records", true, true, false, names, result);
            readPunishments(context, source, prefix + "player_mutes", false, false, true, names, result);
            readPunishments(context, source, prefix + "player_mute_records", false, false, false, names, result);
            readPunishments(context, source, prefix + "ip_mutes", false, true, true, names, result);
            readPunishments(context, source, prefix + "ip_mute_records", false, true, false, names, result);
            readWarnings(context, source, prefix + "player_warnings", names, result);
        }
        return result;
    }

    private static NameHistory playerNames(SourceConnection source, String prefix) throws SQLException, ImportException {
        final NameHistory names = new NameHistory();
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + source.table(prefix + "players"));
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                names.add(SourceConnection.uuid(rs, "id"), rs.getString("name"), rs.getLong("lastSeen"));
            }
        }
        return names;
    }

    /**
     * @param active true for the tables of punishments in force, false for {@code *_records} (removed or ended)
     */
    private static void readPunishments(ImportContext context, SourceConnection source, String table, boolean ban,
                                        boolean byAddress, boolean active, NameHistory names,
                                        ImportResult result) throws SQLException {
        final Optional<String> quoted = source.findTable(table);
        if (quoted.isEmpty()) {
            result.skip("table " + table + " not found (older BanManager or renamed table)");
            return;
        }
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + quoted.get());
             ResultSet rs = statement.executeQuery()) {
            final boolean hasPaused = SourceConnection.hasColumn(rs, active ? "pausedRemaining" : "remainingOnlineTime");
            final boolean hasCreatedReason = !active && SourceConnection.hasColumn(rs, "createdReason");
            final long now = System.currentTimeMillis() / 1000L;
            while (rs.next()) {
                UUID uuid = null;
                String address = null;
                String nickname;
                if (byAddress) {
                    address = SourceConnection.address(rs, "ip");
                    if (!ImportContext.validAddress(address)) {
                        result.skip("IP punishment without a valid address");
                        continue;
                    }
                    nickname = ImportContext.nameForAddress(address);
                } else {
                    uuid = SourceConnection.uuid(rs, "player_id");
                    nickname = names.victimName(uuid);
                    if (!ImportContext.validName(nickname)) {
                        result.skip("player not found in BanManager's players table");
                        continue;
                    }
                }

                final long start = rs.getLong(active ? "created" : "pastCreated");
                long expires = rs.getLong(active ? "expires" : "expired");
                final long paused = hasPaused ? rs.getLong(active ? "pausedRemaining" : "remainingOnlineTime") : 0L;
                if (expires == 0L && paused > 0L) {
                    // Online-only mute of a player who was offline: the time left only runs while they are online.
                    // Judicator has no online-only mutes, so it ends that long from now (or from its removal).
                    expires = (active ? now : rs.getLong("created")) + paused;
                }
                final boolean permanent = expires == 0L;
                final LocalDateTime startedAt = ImportContext.fromEpochMillis(start * 1000L);
                final LocalDateTime finishAt = permanent ? null : ImportContext.fromEpochMillis(expires * 1000L);
                final String operator = names.latestName(SourceConnection.uuid(rs, active ? "actor_id" : "pastActor_id"));

                if (active) {
                    result.add(context.punishment(uuid, nickname, address, rs.getString("reason"), operator,
                            ban, startedAt, finishAt, permanent, true, SOURCE));
                    continue;
                }
                // A record is written when a punishment is removed or when it runs out: it was revoked only if the
                // removal (created) came before the end.
                final long removedAt = rs.getLong("created");
                final boolean revoked = permanent || removedAt < expires;
                result.add(context.punishment(uuid, nickname, address, rs.getString("reason"), operator,
                        ban, startedAt, finishAt, permanent, revoked,
                        hasCreatedReason ? rs.getString("createdReason") : null, SOURCE));
            }
        }
    }

    private static void readWarnings(ImportContext context, SourceConnection source, String table,
                                     NameHistory names, ImportResult result) throws SQLException {
        final Optional<String> quoted = source.findTable(table);
        if (quoted.isEmpty()) {
            result.skip("table " + table + " not found (older BanManager or renamed table)");
            return;
        }
        try (PreparedStatement statement = source.connection().prepareStatement("SELECT * FROM " + quoted.get());
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                final UUID uuid = SourceConnection.uuid(rs, "player_id");
                final String nickname = names.victimName(uuid);
                if (!ImportContext.validName(nickname)) {
                    result.skip("player not found in BanManager's players table");
                    continue;
                }
                final long expires = rs.getLong("expires");
                final boolean permanent = expires == 0L;
                result.add(context.warn(uuid, nickname, rs.getString("reason"), names.latestName(SourceConnection.uuid(rs, "actor_id")),
                        ImportContext.fromEpochMillis(rs.getLong("created") * 1000L),
                        permanent ? null : ImportContext.fromEpochMillis(expires * 1000L), permanent, true));
            }
        }
    }
}
