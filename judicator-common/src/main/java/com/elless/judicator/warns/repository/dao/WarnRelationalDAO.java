package com.elless.judicator.warns.repository.dao;

import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.db.IdentitySql;
import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.commons.db.SqlTime;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.data.repository.PunishmentRepository;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.repository.WarnRepository;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class WarnRelationalDAO implements WarnRepository {

    private final RelationalDBManager manager;
    private final Logger logger;

    public WarnRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    private String generateSaveSQL(boolean isUpdate) {
        final String sql;
        if (isUpdate) {
            sql = """
                        UPDATE {warns} SET
                            player_uuid = ?, reason = ?, punisher = ?, nickname = ?,
                            lower_nickname = ?, started_at = ?, finish_at = ?,
                            permanent = ?, punisher_uuid = ?
                        WHERE id = ?
                    """;
        } else {
            sql = """
                        INSERT INTO {warns} (
                            player_uuid, reason, punisher, nickname, lower_nickname,
                            started_at, finish_at, permanent, punisher_uuid
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        }
        return sql;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Dialect dialect = manager.getDialect();
            final PreparedStatement statement;
            if (dialect == Dialect.POSTGRESQL) {
                statement = connection.prepareStatement(
                        manager.sql("""
                                CREATE TABLE IF NOT EXISTS {warns} (
                                    id BIGSERIAL PRIMARY KEY,
                                    player_uuid VARCHAR(36) NOT NULL,
                                    reason VARCHAR(255) NOT NULL,
                                    punisher VARCHAR(20) NOT NULL,
                                    nickname VARCHAR(20),
                                    lower_nickname VARCHAR(20),
                                    started_at TIMESTAMP NOT NULL,
                                    finish_at TIMESTAMP,
                                    permanent BOOLEAN NOT NULL,
                                    punisher_uuid VARCHAR(36)
                                );
                                """)
                );
            } else if (dialect == Dialect.SQLITE) {
                statement = connection.prepareStatement(manager.sql("""
                        CREATE TABLE IF NOT EXISTS {warns} (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid VARCHAR(36) NOT NULL,
                            reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL,
                            nickname VARCHAR(20),
                            lower_nickname VARCHAR(20),
                            started_at TIMESTAMP NOT NULL,
                            finish_at TIMESTAMP,
                            permanent BOOLEAN NOT NULL,
                            punisher_uuid VARCHAR(36)
                        );
                        """));
            } else {
                statement = connection.prepareStatement(manager.sql("""
                        CREATE TABLE IF NOT EXISTS {warns} (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36) NOT NULL,
                            reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL,
                            nickname VARCHAR(20),
                            lower_nickname VARCHAR(20),
                            started_at DATETIME NOT NULL,
                            finish_at DATETIME,
                            permanent BOOLEAN NOT NULL,
                            punisher_uuid VARCHAR(36)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """));
            }
            statement.execute();

            manager.ensureIndex(connection, "warns", "player_uuid");
            manager.ensureIndex(connection, "warns", "lower_nickname");
            // tables created before 2.0
            manager.ensureColumn(connection, "warns", "punisher_uuid", "VARCHAR(36)");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Warn> findById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {warns} WHERE id = ?
                    """));
            statement.setLong(1, id);
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return Optional.of(load(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Warn> findAllByUsername(String username) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {warns} WHERE lower_nickname = ?
                    """));
            statement.setString(1, username.toLowerCase());
            final ResultSet resultSet = statement.executeQuery();
            final List<Warn> warns = new ArrayList<>();
            while (resultSet.next()) {
                warns.add(load(resultSet));
            }
            return warns;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public Long countActiveWarns(PlayerRef player) {
        final LocalDateTime now = LocalDateTime.now();
        return findAllOf(player).stream()
                .filter(warn -> warn.isPermanent() || warn.getFinishAt().map(end -> end.isAfter(now)).orElse(false))
                .count();
    }

    /**
     * A warn of a name nobody had seen holds the offline-mode UUID of the name as it was typed, and that UUID depends
     * on the letter case, so the rows are narrowed in SQL (the account's UUID or the name) and checked here with
     * {@link Warn#getAccountUuid}.
     */
    @Override
    public List<Warn> findAllOf(PlayerRef player) {
        final String sql = player.identifiedByUuid()
                ? "SELECT * FROM {warns} WHERE player_uuid = ? OR lower_nickname = ?"
                : "SELECT * FROM {warns} WHERE lower_nickname = ?";
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            int index = 1;
            if (player.identifiedByUuid()) statement.setString(index++, player.uuid().toString());
            statement.setString(index, player.lowerName());
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Warn> warns = new ArrayList<>();
                while (resultSet.next()) {
                    final Warn warn = load(resultSet);
                    if (player.matches(warn.getAccountUuid().orElse(null), warn.getNickname())) warns.add(warn);
                }
                return warns;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public int fillUuid(PlayerRef account) {
        final List<Warn> unknown = findAllOf(PlayerRef.byName(account.name(), null)).stream()
                .filter(warn -> warn.getAccountUuid().isEmpty())
                .toList();
        return updateUuids(unknown, account);
    }

    private int updateUuids(List<Warn> warns, PlayerRef account) {
        if (warns.isEmpty()) return 0;
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("UPDATE {warns} SET player_uuid = ? WHERE id = ?"))) {
            for (Warn warn : warns) {
                statement.setString(1, account.uuid().toString());
                statement.setLong(2, warn.getId());
                statement.addBatch();
            }
            statement.executeBatch();
            return warns.size();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<PunishmentRepository.StaffCount> countByStaffSince(LocalDateTime since) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     manager.sql("SELECT MAX(punisher) AS punisher, COUNT(*) AS total FROM {warns} WHERE started_at >= ? GROUP BY COALESCE(punisher_uuid, LOWER(punisher))"))) {
            statement.setTimestamp(1, SqlTime.of(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<PunishmentRepository.StaffCount> counts = new ArrayList<>();
                while (resultSet.next()) {
                    counts.add(new PunishmentRepository.StaffCount(resultSet.getString("punisher"), "WARN", resultSet.getInt("total")));
                }
                return counts;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Warn> findByPunisherSince(PlayerRef staff, LocalDateTime since) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT * FROM {warns} WHERE "
                     + IdentitySql.of(staff, "punisher_uuid", "LOWER(punisher)") + " AND started_at >= ? ORDER BY started_at DESC, id DESC"))) {
            statement.setTimestamp(IdentitySql.bind(statement, 1, staff), SqlTime.of(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Warn> warns = new ArrayList<>();
                while (resultSet.next()) {
                    warns.add(load(resultSet));
                }
                return warns;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public int deleteFinishedBefore(LocalDateTime cutoff) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     manager.sql("DELETE FROM {warns} WHERE permanent = false AND finish_at IS NOT NULL AND finish_at < ?"))) {
            statement.setTimestamp(1, SqlTime.of(cutoff));
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public int deleteAllOf(PlayerRef player) {
        final List<Warn> warns = findAllOf(player);
        if (warns.isEmpty()) return 0;
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("DELETE FROM {warns} WHERE id = ?"))) {
            for (Warn warn : warns) {
                statement.setLong(1, warn.getId());
                statement.addBatch();
            }
            statement.executeBatch();
            return warns.size();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Warn> findAllByUUID(UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {warns} WHERE player_uuid = ?
                    """));
            statement.setString(1, uuid.toString());
            final ResultSet resultSet = statement.executeQuery();
            final List<Warn> warns = new ArrayList<>();
            while (resultSet.next()) {
                warns.add(load(resultSet));
            }
            return warns;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public Warn save(Warn warn) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean isUpdate = warn.getId() != null;
            final String sql = generateSaveSQL(isUpdate);

            try (PreparedStatement stmt = connection.prepareStatement(manager.sql(sql), Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, warn.getPlayerUUID().map(UUID::toString).orElse(null));
                stmt.setString(2, warn.getReason());
                stmt.setString(3, warn.getPunisher());
                stmt.setString(4, warn.getNickname());
                stmt.setString(5, warn.getNickname().toLowerCase());
                stmt.setTimestamp(6, SqlTime.of(warn.getStartedAt()));
                stmt.setTimestamp(7, SqlTime.of(warn.getFinishAt().orElse(null)));
                stmt.setBoolean(8, warn.isPermanent());
                stmt.setString(9, warn.getPunisherUuid().map(UUID::toString).orElse(null));

                if (isUpdate) stmt.setLong(10, warn.getId());
                stmt.executeUpdate();

                if (!isUpdate) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            warn.setId(rs.getLong(1));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return warn;
    }

    @Override
    public void deleteById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(
                    manager.sql("""
                            DELETE FROM {warns} WHERE id = ?
                            """)
            );
            statement.setLong(1, id);
            statement.execute();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Set<String> findAllImportKeys() throws SQLException {
        final Set<String> keys = new HashSet<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT player_uuid, lower_nickname, started_at FROM {warns}"));
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                keys.add(importKey(resultSet.getString("player_uuid"), resultSet.getString("lower_nickname"),
                        SqlTime.read(resultSet, "started_at")));
            }
        }
        return keys;
    }

    @Override
    public int insertAll(List<Warn> warns) throws SQLException {
        if (warns.isEmpty()) return 0;
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement stmt = connection.prepareStatement(manager.sql(generateSaveSQL(false)))) {
                for (Warn warn : warns) {
                    stmt.setString(1, warn.getPlayerUUID().map(UUID::toString).orElse(null));
                    stmt.setString(2, warn.getReason());
                    stmt.setString(3, warn.getPunisher());
                    stmt.setString(4, warn.getNickname());
                    stmt.setString(5, warn.getNickname().toLowerCase());
                    stmt.setTimestamp(6, SqlTime.of(warn.getStartedAt()));
                    stmt.setTimestamp(7, SqlTime.of(warn.getFinishAt().orElse(null)));
                    stmt.setBoolean(8, warn.isPermanent());
                    stmt.setString(9, warn.getPunisherUuid().map(UUID::toString).orElse(null));
                    stmt.addBatch();
                }
                stmt.executeBatch();
                connection.commit();
                return warns.size();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    /**
     * Identity of a warn for imports: UUID (else name) and start to the second.
     */
    public static String importKey(String uuid, String lowerNickname, LocalDateTime startedAt) {
        final String target = uuid != null && !uuid.isBlank() ? "uuid:" + uuid.toLowerCase()
                : "name:" + (lowerNickname == null ? "" : lowerNickname.toLowerCase());
        final String start = startedAt == null ? "" : startedAt.truncatedTo(ChronoUnit.SECONDS).toString();
        return target + "|" + start;
    }

    private Warn load(ResultSet rs) throws SQLException {
        final Warn warn = new Warn();
        warn.setId(rs.getLong("id"));
        warn.setPlayerUUID(UUID.fromString(rs.getString("player_uuid")));
        warn.setNickname(rs.getString("nickname"));
        warn.setPunisher(rs.getString("punisher"));
        warn.setReason(rs.getString("reason"));
        warn.setPermanent(rs.getBoolean("permanent"));
        warn.setStartedAt(SqlTime.read(rs, "started_at"));
        warn.setFinishAt(SqlTime.read(rs, "finish_at"));
        final String punisherUuid = rs.getString("punisher_uuid");
        warn.setPunisherUuid(punisherUuid == null || punisherUuid.isBlank() ? null : UUID.fromString(punisherUuid));
        return warn;
    }

}
