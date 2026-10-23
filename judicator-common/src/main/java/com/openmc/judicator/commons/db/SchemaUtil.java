package com.openmc.judicator.commons.db;

import java.sql.*;

public class SchemaUtil {

    /**
     * Adds a column that tables created by an older version lack ({@code ALTER TABLE … ADD COLUMN}). Nothing happens
     * when it is there; another server sharing the database adding it at the same moment is not an error.
     *
     * @param definition type and constraints, valid in every dialect (e.g. {@code BOOLEAN NOT NULL DEFAULT FALSE})
     */
    public static void ensureColumnExists(Connection connection, String table, String column, String definition) throws SQLException {
        if (hasColumn(connection, table, column)) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            if (!hasColumn(connection, table, column)) throw e;
        }
    }

    /**
     * Asks the database itself (a query that fails only when the column does not exist), so the answer is right in
     * every dialect and for the schema the connection uses, unlike metadata lookups that may see other databases.
     */
    static boolean hasColumn(Connection connection, String table, String column) {
        try (Statement stmt = connection.createStatement();
             ResultSet ignored = stmt.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1 = 0")) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Whether the table exists, asking the database itself like {@link #hasColumn}. Unlike it, only the error of a
     * missing table means "no": any other failure (connection lost, no permission) is thrown, because a migration that
     * took it for "no table" would leave data behind.
     */
    public static boolean tableExists(Connection connection, String table) throws SQLException {
        return probe(connection, "SELECT 1 FROM " + table + " WHERE 1 = 0", "42P01", "42S02", "no such table");
    }

    /**
     * {@link #tableExists} for a column of an existing table.
     */
    public static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        return probe(connection, "SELECT " + column + " FROM " + table + " WHERE 1 = 0", "42703", "42S22", "no such column");
    }

    /**
     * @param postgres the SQLSTATE of PostgreSQL for the missing object
     * @param mysql    the SQLSTATE of MySQL/MariaDB
     * @param sqlite   the message of SQLite, which gives no SQLSTATE
     */
    private static boolean probe(Connection connection, String query, String postgres, String mysql, String sqlite) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet ignored = stmt.executeQuery(query)) {
            return true;
        } catch (SQLException e) {
            final String state = e.getSQLState();
            final String message = String.valueOf(e.getMessage());
            if (postgres.equals(state) || mysql.equals(state) || message.contains(sqlite)) return false;
            throw e;
        }
    }

    public static long countRows(Connection connection, String table) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public static void ensureIndexExists(Connection connection, String tableName, String indexName, String createIndexSQL) throws SQLException {
        if (connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sqlite")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createIndexSQL.replaceFirst("(?i)^CREATE INDEX ", "CREATE INDEX IF NOT EXISTS "));
            }
            return;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                String existingIndex = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(existingIndex)) {
                    return;
                }
            }
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createIndexSQL);
        }
    }
}
