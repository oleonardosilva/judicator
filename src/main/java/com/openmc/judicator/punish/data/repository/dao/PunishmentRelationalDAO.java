package com.openmc.judicator.punish.data.repository.dao;

import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SchemaUtil;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.PunishmentRepository;
import com.openmc.judicator.punish.types.PunishType;
import org.slf4j.Logger;

import java.sql.*;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class PunishmentRelationalDAO implements PunishmentRepository {

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
            final String dbProduct = connection.getMetaData().getDatabaseProductName().toLowerCase();
            final PreparedStatement statement;
            if (dbProduct.contains("postgresql")) {
                statement = connection.prepareStatement(
                        """
                                CREATE TABLE IF NOT EXISTS punishments (
                                    id BIGSERIAL PRIMARY KEY,
                                    player_uuid VARCHAR(36) NOT NULL,
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
            } else {
                statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS punishments (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36) NOT NULL,
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
                      AND (finish_at IS NULL OR finish_at > CURRENT_TIMESTAMP)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, username.toLowerCase());
            for (int i = 0; i < types.length; i++) {
                statement.setString(i + 2, types[i].toString()); // começa no índice 2
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
                      AND (finish_at IS NULL OR finish_at > CURRENT_TIMESTAMP)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, username.toLowerCase());
            statement.setString(2, ipAddress.toLowerCase());
            for (int i = 0; i < types.length; i++) {
                statement.setString(i + 3, types[i].toString()); // começa no índice 2
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
                      AND (finish_at IS NULL OR finish_at > CURRENT_TIMESTAMP)
                      AND type IN (%s)
                    """.formatted(placeholders);

            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, uuid.toString());

            for (int i = 0; i < types.length; i++) {
                statement.setString(i + 2, types[i].toString()); // começa do índice 2
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
                stmt.setString(1, punishment.getPlayerUUID().toString());
                stmt.setString(2, punishment.getReason().orElse(null));
                stmt.setString(3, punishment.getPunisher());
                stmt.setString(4, punishment.getNickname());
                stmt.setString(5, punishment.getIpAddress().orElse(null));
                stmt.setString(6, punishment.getNickname().toLowerCase());
                stmt.setTimestamp(7, Timestamp.from(punishment.getStartedAt().toInstant(ZoneOffset.UTC)));
                stmt.setTimestamp(8, punishment.getFinishAt().isPresent() ? Timestamp.from(punishment.getFinishAt().get().toInstant(ZoneOffset.UTC)) : null);
                stmt.setBoolean(9, punishment.isRevoked());
                stmt.setString(10, String.join(",", punishment.getEvidences()));
                stmt.setString(11, punishment.getType().name());
                stmt.setBoolean(12, punishment.isPermanent());
                stmt.setString(13, punishment.getRevokedReason().orElse(null));

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

    private Punishment load(ResultSet rs) throws SQLException {
        final Punishment punishment = new Punishment();
        punishment.setId(rs.getLong("id"));
        punishment.setPlayerUUID(UUID.fromString(rs.getString("player_uuid")));
        punishment.setIpAddress(rs.getString("ip_address"));
        punishment.setNickname(rs.getString("nickname"));
        punishment.setPunisher(rs.getString("punisher"));
        punishment.setReason(rs.getString("reason"));
        punishment.setPermanent(rs.getBoolean("permanent"));
        punishment.setRevoked(rs.getBoolean("revoked"));
        punishment.setRevokedReason(rs.getString("revoked_reason"));
        punishment.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
        punishment.setFinishAt(Optional.ofNullable(rs.getTimestamp("finish_at")).map(Timestamp::toLocalDateTime).orElse(null));
        punishment.setEvidences(new ArrayList<>(Arrays.stream(rs.getString("evidences").split(",")).filter(s -> !s.isBlank()).toList()));
        punishment.setType(PunishType.valueOf(rs.getString("type")));
        return punishment;
    }

}
