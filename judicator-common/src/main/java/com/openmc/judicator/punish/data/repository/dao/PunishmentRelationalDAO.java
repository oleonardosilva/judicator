package com.openmc.judicator.punish.data.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SchemaUtil;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.types.PunishType;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class PunishmentRelationalDAO implements PunishmentRepository {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final RelationalDBManager manager;
    private final Logger logger;

    public PunishmentRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    private String generateSaveSQL(boolean isUpdate) {
        final String sql;
        if (isUpdate) {
            sql = """
                        UPDATE punishments SET
                            player_uuid = ?, reason = ?, punisher = ?, nickname = ?, ip_address = ?,
                            lower_nickname = ?, started_at = ?, finish_at = ?, revoked = ?, evidences = ?,
                            type = ?, permanent = ?, revoked_reason = ?
                        WHERE id = ?
                    """;
        } else {
            sql = """
                        INSERT INTO punishments (
                            player_uuid, reason, punisher, nickname, ip_address, lower_nickname,
                            started_at, finish_at, revoked, evidences, type, permanent, revoked_reason
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                                CREATE TABLE IF NOT EXISTS punishments (
                                    id BIGSERIAL PRIMARY KEY,
                                    player_uuid VARCHAR(36),
                                    reason VARCHAR(255) NOT NULL,
                                    punisher VARCHAR(20) NOT NULL,
                                    nickname VARCHAR(20),
                                    ip_address VARCHAR(45),
                                    lower_nickname VARCHAR(20),
                                    started_at TIMESTAMP NOT NULL,
                                    finish_at TIMESTAMP,
                                    revoked BOOLEAN NOT NULL,
                                    evidences VARCHAR(255),
                                    type VARCHAR(20) NOT NULL,
                                    permanent BOOLEAN NOT NULL,
                                    revoked_reason VARCHAR(100)
                                );
                                """
                );
            } else if (dialect == Dialect.SQLITE) {
                statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS punishments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid VARCHAR(36),
                            reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL,
                            nickname VARCHAR(20),
                            ip_address VARCHAR(45),
                            lower_nickname VARCHAR(20),
                            started_at TIMESTAMP NOT NULL,
                            finish_at TIMESTAMP,
                            revoked BOOLEAN NOT NULL,
                            evidences VARCHAR(255),
                            type VARCHAR(20) NOT NULL,
                            permanent BOOLEAN NOT NULL,
                            revoked_reason VARCHAR(100)
                        );
                        """);
            } else {
                statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS punishments (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36),
                            reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL,
                            nickname VARCHAR(20),
                            ip_address VARCHAR(45),
                            lower_nickname VARCHAR(20),
                            started_at DATETIME NOT NULL,
                            finish_at DATETIME,
                            revoked BOOLEAN NOT NULL,
                            evidences VARCHAR(255),
                            type VARCHAR(20) NOT NULL,
                            permanent BOOLEAN NOT NULL,
                            revoked_reason VARCHAR(100)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """);
            }
            statement.execute();

            SchemaUtil.ensureIndexExists(
                    connection,
                    "punishments",
                    "idx_punishments_player_uuid",
                    "CREATE INDEX idx_punishments_player_uuid ON punishments(player_uuid)"
            );
            SchemaUtil.ensureIndexExists(
                    connection,
                    "punishments",
                    "idx_punishments_ip_address",
                    "CREATE INDEX idx_punishments_ip_address ON punishments(ip_address)"
            );
            SchemaUtil.ensureIndexExists(
                    connection,
                    "punishments",
                    "idx_punishments_lower_nickname",
                    "CREATE INDEX idx_punishments_lower_nickname ON punishments(lower_nickname)"
            );
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Punishment> findById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM punishments WHERE id = ?
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
    public List<Punishment> findAllByUsername(String username) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM punishments WHERE lower_nickname = ?
                    """);
            statement.setString(1, username.toLowerCase());
            final ResultSet resultSet = statement.executeQuery();
            final List<Punishment> punishments = new ArrayList<>();
            while (resultSet.next()) {
                punishments.add(load(resultSet));
            }
            return punishments;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Punishment> findAllActiveByUsernameAndTypes(String username, PunishType... types) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final String placeholders = Arrays.stream(types).map(t -> "?").collect(Collectors.joining(", "));
            final String sql = """
                    SELECT * FROM punishments
                    WHERE lower_nickname = ?
                      AND revoked = false
                      AND (finish_at IS NULL OR finish_at > ?)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, username.toLowerCase());
            statement.setTimestamp(2, SqlTime.now());
            for (int i = 0; i < types.length; i++) {
                statement.setString(i + 3, types[i].toString());
            }

            final ResultSet resultSet = statement.executeQuery();
            final List<Punishment> punishments = new ArrayList<>();
            while (resultSet.next()) {
                punishments.add(load(resultSet));
            }
            return punishments;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Punishment> findAllActiveByUsernameOrIpAndTypes(String username, String ipAddress, PunishType... types) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final String placeholders = Arrays.stream(types).map(t -> "?").collect(Collectors.joining(", "));
            final String sql = """
                    SELECT * FROM punishments
                    WHERE (lower_nickname = ? OR ip_address = ?)
                      AND revoked = false
                      AND (finish_at IS NULL OR finish_at > ?)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, username.toLowerCase());
            statement.setString(2, ipAddress.toLowerCase());
            statement.setTimestamp(3, SqlTime.now());
            for (int i = 0; i < types.length; i++) {
                statement.setString(i + 4, types[i].toString());
            }

            final ResultSet resultSet = statement.executeQuery();
            final List<Punishment> punishments = new ArrayList<>();
            while (resultSet.next()) {
                punishments.add(load(resultSet));
            }
            return punishments;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Punishment> findAllByUUID(UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM punishments WHERE player_uuid = ?
                    """);
            statement.setString(1, uuid.toString());
            final ResultSet resultSet = statement.executeQuery();
            final List<Punishment> punishments = new ArrayList<>();
            while (resultSet.next()) {
                punishments.add(load(resultSet));
            }
            return punishments;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Punishment> findAllActiveByUUIDAndTypes(UUID uuid, PunishType... types) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final String placeholders = Arrays.stream(types)
                    .map(t -> "?")
                    .collect(Collectors.joining(", "));
            final String sql = """
                    SELECT * FROM punishments
                    WHERE player_uuid = ?
                      AND revoked = false
                      AND (finish_at IS NULL OR finish_at > ?)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, uuid.toString());
            statement.setTimestamp(2, SqlTime.now());

            for (int i = 0; i < types.length; i++) {
                statement.setString(i + 3, types[i].toString());
            }

            final ResultSet resultSet = statement.executeQuery();
            final List<Punishment> punishments = new ArrayList<>();
            while (resultSet.next()) {
                punishments.add(load(resultSet));
            }
            return punishments;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }


    @Override
    public Punishment save(Punishment punishment) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean isUpdate = punishment.getId() != null;
            final String sql = generateSaveSQL(isUpdate);

            try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(stmt, punishment);
                if (isUpdate) stmt.setLong(14, punishment.getId());
                stmt.executeUpdate();

                if (!isUpdate) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            punishment.setId(rs.getLong(1));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return punishment;
    }

    @Override
    public boolean revoke(Long id, String reason) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement st = connection.prepareStatement("""
                    UPDATE punishments
                    SET revoked = true, revoked_reason = ?
                    WHERE id = ?
                    AND revoked = false
                    """);
            st.setString(1, reason);
            st.setLong(2, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void deleteById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(
                    """
                            DELETE FROM punishments WHERE id = ?
                            """
            );
            statement.setLong(1, id);
            statement.execute();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement stmt, Punishment punishment) throws SQLException {
        stmt.setString(1, punishment.getPlayerUUID().map(UUID::toString).orElse(null));
        stmt.setString(2, punishment.getReason().orElse(null));
        stmt.setString(3, punishment.getPunisher());
        stmt.setString(4, punishment.getNickname());
        stmt.setString(5, punishment.getIpAddress().orElse(null));
        stmt.setString(6, punishment.getNickname().toLowerCase());
        stmt.setTimestamp(7, SqlTime.of(punishment.getStartedAt()));
        stmt.setTimestamp(8, SqlTime.of(punishment.getFinishAt().orElse(null)));
        stmt.setBoolean(9, punishment.isRevoked());
        stmt.setString(10, String.join(",", punishment.getEvidences()));
        stmt.setString(11, punishment.getType().name());
        stmt.setBoolean(12, punishment.isPermanent());
        stmt.setString(13, punishment.getRevokedReason().orElse(null));
    }

    @Override
    public int countByPunisher(String punisher, LocalDateTime since) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM punishments WHERE LOWER(punisher) = ? AND started_at >= ?")) {
            statement.setString(1, punisher.toLowerCase());
            statement.setTimestamp(2, SqlTime.of(since == null ? EPOCH : since));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Punishment> findByPunisher(String punisher, LocalDateTime since, int limit, int offset) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM punishments WHERE LOWER(punisher) = ? AND started_at >= ? ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?")) {
            statement.setString(1, punisher.toLowerCase());
            statement.setTimestamp(2, SqlTime.of(since == null ? EPOCH : since));
            statement.setInt(3, limit);
            statement.setInt(4, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Punishment> punishments = new ArrayList<>();
                while (resultSet.next()) {
                    punishments.add(load(resultSet));
                }
                return punishments;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    private static String activeByTypes(String select, PunishType... types) {
        final String placeholders = Arrays.stream(types).map(t -> "?").collect(Collectors.joining(", "));
        return select + " FROM punishments WHERE revoked = false AND (finish_at IS NULL OR finish_at > ?) AND type IN (" + placeholders + ")";
    }

    /**
     * @return the index of the next parameter
     */
    private static int bindActive(PreparedStatement statement, PunishType... types) throws SQLException {
        statement.setTimestamp(1, SqlTime.now());
        for (int i = 0; i < types.length; i++) {
            statement.setString(i + 2, types[i].name());
        }
        return types.length + 2;
    }

    @Override
    public int countActiveByTypes(PunishType... types) {
        if (types.length == 0) return 0;
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(activeByTypes("SELECT COUNT(*)", types))) {
            bindActive(statement, types);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Punishment> findActiveByTypes(int limit, int offset, PunishType... types) {
        if (types.length == 0) return List.of();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     activeByTypes("SELECT *", types) + " ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?")) {
            final int next = bindActive(statement, types);
            statement.setInt(next, limit);
            statement.setInt(next + 1, offset);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Punishment> punishments = new ArrayList<>();
                while (resultSet.next()) {
                    punishments.add(load(resultSet));
                }
                return punishments;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<StaffCount> countByStaffSince(LocalDateTime since) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MAX(punisher) AS punisher, type, COUNT(*) AS total FROM punishments "
                             + "WHERE started_at >= ? GROUP BY LOWER(punisher), type")) {
            statement.setTimestamp(1, SqlTime.of(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<StaffCount> counts = new ArrayList<>();
                while (resultSet.next()) {
                    counts.add(new StaffCount(resultSet.getString("punisher"), resultSet.getString("type"), resultSet.getInt("total")));
                }
                return counts;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public Set<String> findAllImportKeys() throws SQLException {
        final Set<String> keys = new HashSet<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, ip_address, lower_nickname, type, started_at FROM punishments");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                keys.add(importKey(resultSet.getString("player_uuid"), resultSet.getString("ip_address"),
                        resultSet.getString("lower_nickname"), resultSet.getString("type"),
                        SqlTime.read(resultSet, "started_at")));
            }
        }
        return keys;
    }

    @Override
    public int insertAll(List<Punishment> punishments) throws SQLException {
        if (punishments.isEmpty()) return 0;
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement stmt = connection.prepareStatement(generateSaveSQL(false))) {
                for (Punishment punishment : punishments) {
                    bind(stmt, punishment);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                connection.commit();
                return punishments.size();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    /**
     * Identity of a punishment for imports: the target (IP for IP punishments, else UUID, else name), the type and
     * the start to the second. Names are the last resort because a source may report a newer name on a re-run.
     */
    public static String importKey(String uuid, String ipAddress, String lowerNickname, String type, LocalDateTime startedAt) {
        final String target;
        if (ipAddress != null && !ipAddress.isBlank()) target = "ip:" + ipAddress.toLowerCase();
        else if (uuid != null && !uuid.isBlank()) target = "uuid:" + uuid.toLowerCase();
        else target = "name:" + (lowerNickname == null ? "" : lowerNickname.toLowerCase());
        final String start = startedAt == null ? "" : startedAt.truncatedTo(ChronoUnit.SECONDS).toString();
        return target + "|" + type + "|" + start;
    }

    public static String importKey(Punishment punishment) {
        return importKey(punishment.getPlayerUUID().map(UUID::toString).orElse(null), punishment.getIpAddress().orElse(null),
                punishment.getNickname(), punishment.getType().name(), punishment.getStartedAt());
    }

    private Punishment load(ResultSet rs) throws SQLException {
        final Punishment punishment = new Punishment();
        punishment.setId(rs.getLong("id"));
        final String uuid = rs.getString("player_uuid");
        punishment.setPlayerUUID(uuid == null || uuid.isBlank() ? null : UUID.fromString(uuid));
        punishment.setIpAddress(rs.getString("ip_address"));
        punishment.setNickname(rs.getString("nickname"));
        punishment.setPunisher(rs.getString("punisher"));
        punishment.setReason(rs.getString("reason"));
        punishment.setPermanent(rs.getBoolean("permanent"));
        punishment.setRevoked(rs.getBoolean("revoked"));
        punishment.setRevokedReason(rs.getString("revoked_reason"));
        punishment.setStartedAt(SqlTime.read(rs, "started_at"));
        punishment.setFinishAt(SqlTime.read(rs, "finish_at"));
        final String evidences = rs.getString("evidences");
        punishment.setEvidences(evidences == null ? new ArrayList<>()
                : new ArrayList<>(Arrays.stream(evidences.split(",")).filter(s -> !s.isBlank()).toList()));
        punishment.setType(PunishType.valueOf(rs.getString("type")));
        return punishment;
    }

}
