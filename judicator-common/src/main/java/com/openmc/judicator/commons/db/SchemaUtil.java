package com.openmc.judicator.commons.db;

import java.sql.*;

public class SchemaUtil {

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
