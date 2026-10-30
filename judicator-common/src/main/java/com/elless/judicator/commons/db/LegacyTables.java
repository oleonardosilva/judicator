package com.elless.judicator.commons.db;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Version 1.x created its tables without a prefix ({@code punishments}, {@code warns}…). On the first start with
 * {@code database.table-prefix}, each one is renamed to its prefixed name with its rows, and its indexes are dropped
 * (the DAOs create the prefixed ones). A table is renamed only when the prefixed one does not exist yet and it has the
 * columns Judicator created: another plugin may own a table with the same name, and that one is left alone.
 */
public final class LegacyTables {

    /**
     * Columns that tell a table of Judicator 1.x from another plugin's table with the same name.
     */
    private static final Map<String, List<String>> SIGNATURES = Map.of(
            "punishments", List.of("lower_nickname", "evidences", "permanent", "revoked_reason"),
            "warns", List.of("lower_nickname", "punisher", "permanent", "finish_at"),
            "notes", List.of("lower_nickname", "author", "content", "created_at"),
            "player_levels", List.of("lower_nickname", "player_uuid", "level", "updated_at"),
            "sync_messages", List.of("sender", "kind", "punishment_id"),
            "access_addresses", List.of("host_address", "accounts", "last_usage"));

    /**
     * Columns indexed by 1.x, as {@code idx_<table>_<column>}.
     */
    private static final Map<String, List<String>> INDEXES = Map.of(
            "punishments", List.of("player_uuid", "ip_address", "lower_nickname"),
            "warns", List.of("player_uuid", "lower_nickname"),
            "notes", List.of("lower_nickname"),
            "access_addresses", List.of("host_address"));

    private LegacyTables() {
    }

    /**
     * Runs before the DAOs create their tables. Nothing happens without a prefix or when there is nothing to rename.
     *
     * @throws IllegalStateException when a table of 1.x could not be checked or renamed: starting anyway would create
     *                               empty prefixed tables next to it, and the bans it holds would no longer be enforced
     */
    public static void migrate(RelationalDBManager manager, Logger logger) {
        if (manager.getTablePrefix().isEmpty() || manager.getDataSource() == null) return;
        try (Connection connection = manager.getDataSource().getConnection()) {
            for (Map.Entry<String, List<String>> table : SIGNATURES.entrySet()) {
                rename(connection, manager, logger, table.getKey(), table.getValue());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not rename the tables of Judicator 1.x to the table-prefix of data.yml ("
                    + e.getMessage() + "). Nothing was changed for the tables left; fix the cause (e.g. the ALTER permission) and start again.", e);
        }
    }

    /**
     * Whether a table has the columns Judicator 1.x created in the table of that name.
     *
     * @param table  the table as it can be written in SQL (quoted, prefixed…)
     * @param legacy the name 1.x gave it, e.g. {@code punishments}
     */
    public static boolean hasColumnsOf(Connection connection, String table, String legacy) throws SQLException {
        for (String column : SIGNATURES.getOrDefault(legacy, List.of())) {
            if (!SchemaUtil.columnExists(connection, table, column)) return false;
        }
        return true;
    }

    private static void rename(Connection connection, RelationalDBManager manager, Logger logger, String legacy,
                               List<String> signature) throws SQLException {
        final String prefixed = manager.table(legacy);
        if (!SchemaUtil.tableExists(connection, legacy)) return;
        if (!hasColumnsOf(connection, legacy, legacy)) {
            logger.warn("Table {} does not have the columns of Judicator 1.x ({}): it was left as it is (Judicator uses {}).",
                    legacy, String.join(", ", signature), prefixed);
            return;
        }
        if (SchemaUtil.tableExists(connection, prefixed)) {
            final long rows = SchemaUtil.countRows(connection, legacy);
            if (rows > 0) {
                logger.warn("Tables {} ({} rows, Judicator 1.x) and {} both exist: the rows of {} are not used. A server still "
                        + "running Judicator 1.x on this database writes there: update it. To keep those rows, import them "
                        + "(/judicator import judicator with this database and table-prefix '' in import.yml).", legacy, rows, prefixed, legacy);
            }
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + legacy + " RENAME TO " + prefixed);
        } catch (SQLException e) {
            // another server sharing the database renamed it at the same moment
            if (SchemaUtil.tableExists(connection, prefixed) && !SchemaUtil.tableExists(connection, legacy)) return;
            throw e;
        }
        logger.info("Renamed table {} to {} (table-prefix in data.yml).", legacy, prefixed);
        for (String column : INDEXES.getOrDefault(legacy, List.of())) {
            dropIndex(connection, manager.getDialect(), "idx_" + legacy + "_" + column, prefixed);
        }
    }

    private static void dropIndex(Connection connection, Dialect dialect, String index, String table) {
        final String sql = dialect == Dialect.MYSQL ? "DROP INDEX " + index + " ON " + table : "DROP INDEX IF EXISTS " + index;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ignored) {
            // already gone (dropped by hand): the prefixed index is created anyway
        }
    }

}
