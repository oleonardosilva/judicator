package com.openmc.judicator.testing;

import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SqlTime;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * The {@code access_addresses} table of Judicator 1.x (one row per IP with a comma-separated list of names), written
 * by hand: 2.0 no longer has a DAO for it, only the copy into {@code player_addresses}.
 */
public final class Version1Addresses {

    private Version1Addresses() {
    }

    /**
     * Creates the table (named as the manager's prefix gives it) when missing and adds one address.
     */
    public static void add(RelationalDBManager manager, String address, String accounts, LocalDateTime lastUsage) {
        final String table = manager.table("access_addresses");
        try (Connection connection = manager.getDataSource().getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "host_address VARCHAR(50) NOT NULL, accounts TEXT NOT NULL, last_usage TIMESTAMP NOT NULL)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_" + table + "_host_address ON " + table + "(host_address)");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table + " (host_address, accounts, last_usage) VALUES (?, ?, ?)")) {
                insert.setString(1, address);
                insert.setString(2, accounts);
                insert.setTimestamp(3, SqlTime.of(lastUsage));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
