package com.openmc.judicator.punish.hierarchy;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SqlTime;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.OptionalInt;
import java.util.UUID;

public class PlayerLevelRelationalDAO implements PlayerLevelRepository {

    private final RelationalDBManager manager;
    private final Logger logger;

    public PlayerLevelRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Dialect dialect = manager.getDialect();
            final String sql = dialect != Dialect.MYSQL ? """
                    CREATE TABLE IF NOT EXISTS player_levels (
                        lower_nickname VARCHAR(20) PRIMARY KEY,
                        player_uuid VARCHAR(36),
                        level INT NOT NULL,
                        updated_at TIMESTAMP NOT NULL
                    );
                    """ : """
                    CREATE TABLE IF NOT EXISTS player_levels (
                        lower_nickname VARCHAR(20) PRIMARY KEY,
                        player_uuid VARCHAR(36),
                        level INT NOT NULL,
                        updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.execute();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void saveLevel(String nickname, UUID uuid, int level) {
        final String key = nickname.toLowerCase();
        final String uuidText = uuid == null ? null : uuid.toString();
        final Timestamp now = SqlTime.now();
        try (Connection connection = manager.getDataSource().getConnection()) {
            if (update(connection, key, uuidText, level, now) > 0) return;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_levels (lower_nickname, player_uuid, level, updated_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, uuidText);
                insert.setInt(3, level);
                insert.setTimestamp(4, now);
                insert.executeUpdate();
            } catch (SQLException e) {
                // Another server sharing the database inserted the same player first: update that row instead.
                if (e.getSQLState() == null || !e.getSQLState().startsWith("23")) throw e;
                update(connection, key, uuidText, level, now);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static int update(Connection connection, String key, String uuid, int level, Timestamp now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE player_levels SET player_uuid = ?, level = ?, updated_at = ? WHERE lower_nickname = ?")) {
            update.setString(1, uuid);
            update.setInt(2, level);
            update.setTimestamp(3, now);
            update.setString(4, key);
            return update.executeUpdate();
        }
    }

    @Override
    public boolean delete(String nickname) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM player_levels WHERE lower_nickname = ?")) {
            statement.setString(1, nickname.toLowerCase());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public OptionalInt findLevel(String nickname) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT level FROM player_levels WHERE lower_nickname = ?")) {
            statement.setString(1, nickname.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) return OptionalInt.of(resultSet.getInt("level"));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return OptionalInt.empty();
    }

}
