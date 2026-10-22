package com.openmc.judicator.warns.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SchemaUtil;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.WarnRepository;
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
                        UPDATE warns SET
                            player_uuid = ?, reason = ?, punisher = ?, nickname = ?,
                            lower_nickname = ?, started_at = ?, finish_at = ?,
                            permanent = ?
                        WHERE id = ?
                    """;
        } else {
            sql = """
                        INSERT INTO warns (
                            player_uuid, reason, punisher, nickname, lower_nickname,
                            started_at, finish_at, permanent
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
                        """
                                CREATE TABLE IF NOT EXISTS warns (
                                    id BIGSERIAL PRIMARY KEY,
                                    player_uuid VARCHAR(36) NOT NULL,
                                    reason VARCHAR(255) NOT NULL,
                                    punisher VARCHAR(20) NOT NULL,
                                    nickname VARCHAR(20),
                                    lower_nickname VARCHAR(20),
                                    started_at TIMESTAMP NOT NULL,
                                    finish_at TIMESTAMP,
                                    permanent BOOLEAN NOT NULL
                                );
                                """
                );
            } else if (dialect == Dialect.SQLITE) {
                statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS warns (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid VARCHAR(36) NOT NULL,
                            reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL,
                            nickname VARCHAR(20),
                            lower_nickname VARCHAR(20),
                            started_at TIMESTAMP NOT NULL,
                            finish_at TIMESTAMP,
                            permanent BOOLEAN NOT NULL
                        );
                        """);
            } else {
                statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS warns (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36) NOT NULL,
                            reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL,
                            nickname VARCHAR(20),
                            lower_nickname VARCHAR(20),
                            started_at DATETIME NOT NULL,
                            finish_at DATETIME,
                            permanent BOOLEAN NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """);
            }
            statement.execute();

            SchemaUtil.ensureIndexExists(
                    connection,
                    "warns",
                    "idx_warns_player_uuid",
                    "CREATE INDEX idx_warns_player_uuid ON warns(player_uuid)"
            );
            SchemaUtil.ensureIndexExists(
                    connection,
                    "warns",
                    "idx_warns_lower_nickname",
                    "CREATE INDEX idx_warns_lower_nickname ON warns(lower_nickname)"
            );
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Warn> findById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM warns WHERE id = ?
                    """);
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
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM warns WHERE lower_nickname = ?
                    """);
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
    public Long countActiveWarns(String username) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT COUNT(*) FROM warns WHERE lower_nickname = ?
                    AND (permanent = true OR finish_at > ?)
                    """);
            statement.setString(1, username.toLowerCase());
            statement.setTimestamp(2, SqlTime.now());
            final ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return 0L;
    }

    @Override
    public List<Warn> findAllByUUID(UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM warns WHERE player_uuid = ?
                    """);
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

            try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, warn.getPlayerUUID().map(UUID::toString).orElse(null));
                stmt.setString(2, warn.getReason());
                stmt.setString(3, warn.getPunisher());
                stmt.setString(4, warn.getNickname());
                stmt.setString(5, warn.getNickname().toLowerCase());
                stmt.setTimestamp(6, SqlTime.of(warn.getStartedAt()));
                stmt.setTimestamp(7, SqlTime.of(warn.getFinishAt().orElse(null)));
                stmt.setBoolean(8, warn.isPermanent());

                if (isUpdate) stmt.setLong(9, warn.getId());
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
                    """
                            DELETE FROM warns WHERE id = ?
                            """
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
             PreparedStatement statement = connection.prepareStatement("SELECT player_uuid, lower_nickname, started_at FROM warns");
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
            try (PreparedStatement stmt = connection.prepareStatement(generateSaveSQL(false))) {
                for (Warn warn : warns) {
                    stmt.setString(1, warn.getPlayerUUID().map(UUID::toString).orElse(null));
                    stmt.setString(2, warn.getReason());
                    stmt.setString(3, warn.getPunisher());
                    stmt.setString(4, warn.getNickname());
                    stmt.setString(5, warn.getNickname().toLowerCase());
                    stmt.setTimestamp(6, SqlTime.of(warn.getStartedAt()));
                    stmt.setTimestamp(7, SqlTime.of(warn.getFinishAt().orElse(null)));
                    stmt.setBoolean(8, warn.isPermanent());
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
        return warn;
    }

}
