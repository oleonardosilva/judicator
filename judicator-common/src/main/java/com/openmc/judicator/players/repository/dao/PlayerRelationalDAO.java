package com.openmc.judicator.players.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.players.KnownPlayer;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.players.repository.PlayerRepository;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerRelationalDAO implements PlayerRepository {

    private final RelationalDBManager manager;
    private final Logger logger;

    public PlayerRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final String sql = manager.getDialect() != Dialect.MYSQL ? """
                    CREATE TABLE IF NOT EXISTS {players} (
                        uuid VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(20) NOT NULL,
                        lower_name VARCHAR(20) NOT NULL,
                        last_seen TIMESTAMP NOT NULL
                    );
                    """ : """
                    CREATE TABLE IF NOT EXISTS {players} (
                        uuid VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(20) NOT NULL,
                        lower_name VARCHAR(20) NOT NULL,
                        last_seen DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """;
            try (PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
                statement.execute();
            }
            manager.ensureIndex(connection, "players", "lower_name");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean record(UUID uuid, String name, LocalDateTime seenAt) {
        final String lower = name.toLowerCase(Locale.ROOT);
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Optional<String> stored = storedLowerName(connection, uuid);
            if (stored.isPresent()) {
                update(connection, uuid, name, seenAt);
                return !stored.get().equals(lower);
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    manager.sql("INSERT INTO {players} (uuid, name, lower_name, last_seen) VALUES (?, ?, ?, ?)"))) {
                insert.setString(1, uuid.toString());
                insert.setString(2, name);
                insert.setString(3, lower);
                insert.setTimestamp(4, SqlTime.of(seenAt));
                insert.executeUpdate();
            } catch (SQLException e) {
                // another server sharing the database inserted the same account first
                if (e.getSQLState() == null || !e.getSQLState().startsWith("23")) throw e;
                update(connection, uuid, name, seenAt);
            }
            return true;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    private Optional<String> storedLowerName(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT lower_name FROM {players} WHERE uuid = ?"))) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty();
            }
        }
    }

    private void update(Connection connection, UUID uuid, String name, LocalDateTime seenAt) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                manager.sql("UPDATE {players} SET name = ?, lower_name = ?, last_seen = ? WHERE uuid = ?"))) {
            update.setString(1, name);
            update.setString(2, name.toLowerCase(Locale.ROOT));
            update.setTimestamp(3, SqlTime.of(seenAt));
            update.setString(4, uuid.toString());
            update.executeUpdate();
        }
    }

    @Override
    public Optional<KnownPlayer> findLatestByName(String name) {
        return findOne("SELECT * FROM {players} WHERE lower_name = ? ORDER BY last_seen DESC", name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Optional<KnownPlayer> findByUuid(UUID uuid) {
        return findOne("SELECT * FROM {players} WHERE uuid = ?", uuid.toString());
    }

    private Optional<KnownPlayer> findOne(String sql, String parameter) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new KnownPlayer(UUID.fromString(resultSet.getString("uuid")), resultSet.getString("name"),
                            SqlTime.read(resultSet, "last_seen")));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            logger.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public int deleteByUuid(UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("DELETE FROM {players} WHERE uuid = ?"))) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public Map<String, UUID> latestUuidsByName() {
        final Map<String, UUID> names = new HashMap<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT uuid, lower_name FROM {players} ORDER BY last_seen"));
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                try {
                    names.put(resultSet.getString("lower_name"), UUID.fromString(resultSet.getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                    // not a UUID: skipped
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return names;
    }

    @Override
    public int seedIfEmpty() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            try (PreparedStatement count = connection.prepareStatement(manager.sql("SELECT COUNT(*) FROM {players}"));
                 ResultSet resultSet = count.executeQuery()) {
                if (resultSet.next() && resultSet.getLong(1) > 0) return 0;
            }
            final Map<UUID, KnownPlayer> latest = new HashMap<>();
            collect(connection, "SELECT player_uuid, nickname, started_at AS seen FROM {punishments} WHERE player_uuid IS NOT NULL", latest, false);
            // a warn of an unknown player stores the offline-mode UUID of the name in place of the missing one
            collect(connection, "SELECT player_uuid, nickname, started_at AS seen FROM {warns}", latest, true);
            collect(connection, "SELECT player_uuid, nickname, created_at AS seen FROM {notes} WHERE player_uuid IS NOT NULL", latest, false);
            // staff levels only keep the name in lower case: used for accounts nothing else names
            final Map<UUID, KnownPlayer> levels = new HashMap<>();
            collect(connection, "SELECT player_uuid, lower_nickname, updated_at AS seen FROM {player_levels} WHERE player_uuid IS NOT NULL", levels, false);
            levels.forEach(latest::putIfAbsent);
            if (latest.isEmpty()) return 0;
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    manager.sql("INSERT INTO {players} (uuid, name, lower_name, last_seen) VALUES (?, ?, ?, ?)"))) {
                for (KnownPlayer player : latest.values()) {
                    insert.setString(1, player.uuid().toString());
                    insert.setString(2, player.name());
                    insert.setString(3, player.name().toLowerCase(Locale.ROOT));
                    insert.setTimestamp(4, SqlTime.of(player.lastSeen()));
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return latest.size();
        } catch (SQLException e) {
            logger.error("Could not fill the players table from the existing records: " + e.getMessage(), e);
            return 0;
        }
    }

    private void collect(Connection connection, String sql, Map<UUID, KnownPlayer> latest, boolean skipOfflineUuids) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(manager.sql(sql));
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final UUID uuid;
                try {
                    uuid = UUID.fromString(resultSet.getString(1));
                } catch (IllegalArgumentException | NullPointerException e) {
                    continue;
                }
                final String name = resultSet.getString(2);
                if (name == null || name.isBlank() || name.length() > 20) continue;
                if (skipOfflineUuids && uuid.equals(PlayerRef.offlineUuid(name))) continue;
                final LocalDateTime seen = SqlTime.read(resultSet, "seen");
                if (seen == null) continue;
                latest.merge(uuid, new KnownPlayer(uuid, name, seen),
                        (old, candidate) -> candidate.lastSeen().isAfter(old.lastSeen()) ? candidate : old);
            }
        }
    }

}
