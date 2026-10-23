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
