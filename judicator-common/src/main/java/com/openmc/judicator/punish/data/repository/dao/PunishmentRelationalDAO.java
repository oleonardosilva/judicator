package com.openmc.judicator.punish.data.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.IdentitySql;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.players.PlayerRef;
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
    private static final int NAMES_PER_QUERY = 200;

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
                        UPDATE {punishments} SET
                            player_uuid = ?, reason = ?, punisher = ?, nickname = ?, ip_address = ?,
                            lower_nickname = ?, started_at = ?, finish_at = ?, revoked = ?, evidences = ?,
                            type = ?, permanent = ?, revoked_reason = ?, soft = ?, online_only = ?, paused_remaining = ?,
                            punisher_uuid = ?
                        WHERE id = ?
                    """;
        } else {
            sql = """
                        INSERT INTO {punishments} (
                            player_uuid, reason, punisher, nickname, ip_address, lower_nickname,
                            started_at, finish_at, revoked, evidences, type, permanent, revoked_reason,
                            soft, online_only, paused_remaining, punisher_uuid
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                                CREATE TABLE IF NOT EXISTS {punishments} (
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
                                    revoked_reason VARCHAR(100),
                                    soft BOOLEAN NOT NULL DEFAULT FALSE,
                                    online_only BOOLEAN NOT NULL DEFAULT FALSE,
                                    paused_remaining BIGINT,
                                    punisher_uuid VARCHAR(36)
                                );
                                """)
                );
            } else if (dialect == Dialect.SQLITE) {
                statement = connection.prepareStatement(manager.sql("""
                        CREATE TABLE IF NOT EXISTS {punishments} (
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
                            revoked_reason VARCHAR(100),
                            soft BOOLEAN NOT NULL DEFAULT FALSE,
                            online_only BOOLEAN NOT NULL DEFAULT FALSE,
                            paused_remaining BIGINT,
                            punisher_uuid VARCHAR(36)
                        );
                        """));
            } else {
                statement = connection.prepareStatement(manager.sql("""
                        CREATE TABLE IF NOT EXISTS {punishments} (
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
                            revoked_reason VARCHAR(100),
                            soft BOOLEAN NOT NULL DEFAULT FALSE,
                            online_only BOOLEAN NOT NULL DEFAULT FALSE,
                            paused_remaining BIGINT,
                            punisher_uuid VARCHAR(36)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """));
            }
            statement.execute();
            // Tables created before soft and online-only mutes existed.
            manager.ensureColumn(connection, "punishments", "soft", "BOOLEAN NOT NULL DEFAULT FALSE");
            manager.ensureColumn(connection, "punishments", "online_only", "BOOLEAN NOT NULL DEFAULT FALSE");
            manager.ensureColumn(connection, "punishments", "paused_remaining", "BIGINT");
            manager.ensureColumn(connection, "punishments", "punisher_uuid", "VARCHAR(36)");

            manager.ensureIndex(connection, "punishments", "player_uuid");
            manager.ensureIndex(connection, "punishments", "ip_address");
            manager.ensureIndex(connection, "punishments", "lower_nickname");
            manager.ensureIndex(connection, "punishments", "punisher_uuid");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public Optional<Punishment> findById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {punishments} WHERE id = ?
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
    public List<Punishment> findAllByUsername(String username) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {punishments} WHERE lower_nickname = ?
                    """));
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
                    SELECT * FROM {punishments}
                    WHERE lower_nickname = ?
                      AND revoked = false
                      AND (finish_at IS NULL OR finish_at > ?)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(manager.sql(sql));
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
    public List<Punishment> findAllOf(PlayerRef player) {
        return query("SELECT * FROM {punishments} WHERE " + IdentitySql.of(player, "player_uuid", "lower_nickname"),
                statement -> IdentitySql.bind(statement, 1, player));
    }

    @Override
    public List<Punishment> findAllActiveOf(PlayerRef player, PunishType... types) {
        if (types.length == 0) return List.of();
        return query(activeByTypes("SELECT *", types) + " AND " + IdentitySql.of(player, "player_uuid", "lower_nickname"),
                statement -> IdentitySql.bind(statement, bindActive(statement, types), player));
    }

    @Override
    public List<Punishment> findAllActiveOfAddress(String ipAddress, PunishType... types) {
        if (types.length == 0) return List.of();
        return query(activeByTypes("SELECT *", types) + " AND ip_address = ?",
                statement -> statement.setString(bindActive(statement, types), ipAddress.toLowerCase()));
    }

    @Override
    public List<Punishment> findAllActiveOfAny(Collection<PlayerRef> players, PunishType... types) {
        if (players.isEmpty() || types.length == 0) return List.of();
        final List<PlayerRef> list = List.copyOf(players);
        final List<Punishment> punishments = new ArrayList<>();
        // A bounded number of placeholders per statement, whatever the number of accounts on the address.
        for (int from = 0; from < list.size(); from += NAMES_PER_QUERY) {
            final List<PlayerRef> chunk = list.subList(from, Math.min(from + NAMES_PER_QUERY, list.size()));
            final String any = chunk.stream().map(player -> IdentitySql.of(player, "player_uuid", "lower_nickname"))
                    .collect(Collectors.joining(" OR "));
            punishments.addAll(query(activeByTypes("SELECT *", types) + " AND (" + any + ")", statement -> {
                int next = bindActive(statement, types);
                for (PlayerRef player : chunk) next = IdentitySql.bind(statement, next, player);
            }));
        }
        return punishments;
    }

    @Override
    public int fillUuid(PlayerRef account) {
        return execute("UPDATE {punishments} SET player_uuid = ? WHERE player_uuid IS NULL AND lower_nickname = ?",
                account.uuid().toString(), account.lowerName());
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private List<Punishment> query(String sql, Binder binder) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<Punishment> punishments = new ArrayList<>();
                while (resultSet.next()) punishments.add(load(resultSet));
                return punishments;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<Punishment> findAllByUUID(UUID uuid) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {punishments} WHERE player_uuid = ?
                    """));
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
    public Punishment save(Punishment punishment) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean isUpdate = punishment.getId() != null;
            final String sql = generateSaveSQL(isUpdate);

            try (PreparedStatement stmt = connection.prepareStatement(manager.sql(sql), Statement.RETURN_GENERATED_KEYS)) {
                bind(stmt, punishment);
                if (isUpdate) stmt.setLong(18, punishment.getId());
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
            final PreparedStatement st = connection.prepareStatement(manager.sql("""
                    UPDATE {punishments}
                    SET revoked = true, revoked_reason = ?
                    WHERE id = ?
                    AND revoked = false
                    """));
            st.setString(1, reason);
            st.setLong(2, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return false;
    }

    @Override
    public boolean changeEnd(Long id, LocalDateTime finishAt, Long pausedRemaining) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     manager.sql("UPDATE {punishments} SET finish_at = ?, paused_remaining = ? WHERE id = ? AND revoked = false"))) {
            statement.setTimestamp(1, SqlTime.of(finishAt));
            if (pausedRemaining == null) statement.setNull(2, Types.BIGINT);
            else statement.setLong(2, pausedRemaining);
            statement.setLong(3, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean changeReason(Long id, String reason) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     manager.sql("UPDATE {punishments} SET reason = ? WHERE id = ? AND revoked = false"))) {
            statement.setString(1, reason);
            statement.setLong(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int deleteKicksBefore(LocalDateTime cutoff) {
        return execute("DELETE FROM {punishments} WHERE type = 'KICK' AND started_at < ?", SqlTime.of(cutoff));
    }

    @Override
    public int deleteFinishedBefore(LocalDateTime cutoff) {
        final Timestamp time = SqlTime.of(cutoff);
        // kicks have their own retention (retention.kicks): a logged kick ends when it starts
        return execute("DELETE FROM {punishments} WHERE type <> 'KICK' AND ((finish_at IS NOT NULL AND finish_at < ?) OR (revoked = true AND started_at < ?))",
                time, time);
    }

    @Override
    public int deleteAllOf(PlayerRef player) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(
                     "DELETE FROM {punishments} WHERE " + IdentitySql.of(player, "player_uuid", "lower_nickname")))) {
            IdentitySql.bind(statement, 1, player);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    private int execute(String sql, Object... parameters) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public void deleteById(Long id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(
                    manager.sql("""
                            DELETE FROM {punishments} WHERE id = ?
                            """)
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
        stmt.setBoolean(14, punishment.isSoft());
        stmt.setBoolean(15, punishment.isOnlineOnly());
        if (punishment.getPausedRemaining() == null) stmt.setNull(16, Types.BIGINT);
        else stmt.setLong(16, punishment.getPausedRemaining());
        stmt.setString(17, punishment.getPunisherUuid().map(UUID::toString).orElse(null));
    }

    @Override
    public int countByPunisher(PlayerRef staff, LocalDateTime since) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT COUNT(*) FROM {punishments} WHERE "
                     + IdentitySql.of(staff, "punisher_uuid", "LOWER(punisher)") + " AND started_at >= ?"))) {
            final int next = IdentitySql.bind(statement, 1, staff);
            statement.setTimestamp(next, SqlTime.of(since == null ? EPOCH : since));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Punishment> findByPunisher(PlayerRef staff, LocalDateTime since, int limit, int offset) {
        return query("SELECT * FROM {punishments} WHERE " + IdentitySql.of(staff, "punisher_uuid", "LOWER(punisher)")
                + " AND started_at >= ? ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?", statement -> {
            final int next = IdentitySql.bind(statement, 1, staff);
            statement.setTimestamp(next, SqlTime.of(since == null ? EPOCH : since));
            statement.setInt(next + 1, limit);
            statement.setInt(next + 2, offset);
        });
    }

    private static String activeByTypes(String select, PunishType... types) {
        final String placeholders = Arrays.stream(types).map(t -> "?").collect(Collectors.joining(", "));
        return select + " FROM {punishments} WHERE revoked = false AND (finish_at IS NULL OR finish_at > ?) AND type IN (" + placeholders + ")";
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
             PreparedStatement statement = connection.prepareStatement(manager.sql(activeByTypes("SELECT COUNT(*)", types)))) {
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
                     manager.sql(activeByTypes("SELECT *", types) + " ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?"))) {
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
                     manager.sql("SELECT MAX(punisher) AS punisher, type, COUNT(*) AS total FROM {punishments} "
                             + "WHERE started_at >= ? GROUP BY COALESCE(punisher_uuid, LOWER(punisher)), type"))) {
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
                     manager.sql("SELECT player_uuid, ip_address, lower_nickname, type, started_at FROM {punishments}"));
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
            try (PreparedStatement stmt = connection.prepareStatement(manager.sql(generateSaveSQL(false)))) {
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
        punishment.setPlayerUUID(uuid(rs.getString("player_uuid")));
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
        punishment.setSoft(rs.getBoolean("soft"));
        punishment.setOnlineOnly(rs.getBoolean("online_only"));
        final long paused = rs.getLong("paused_remaining");
        punishment.setPausedRemaining(rs.wasNull() ? null : paused);
        punishment.setPunisherUuid(uuid(rs.getString("punisher_uuid")));
        return punishment;
    }

    private static UUID uuid(String text) {
        return text == null || text.isBlank() ? null : UUID.fromString(text);
    }

}
