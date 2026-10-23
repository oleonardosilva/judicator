package com.openmc.judicator.punish.data.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.IdentitySql;
import com.openmc.judicator.commons.db.LegacyTables;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SchemaUtil;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.punish.PlayerAddress;
import com.openmc.judicator.punish.data.repository.PlayerAddressRepository;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class PlayerAddressRelationalDAO implements PlayerAddressRepository {

    private static final String INSERT =
            "INSERT INTO {player_addresses} (player_uuid, nickname, lower_nickname, host_address, last_usage) VALUES (?, ?, ?, ?, ?)";

    private final RelationalDBManager manager;
    private final Logger logger;

    public PlayerAddressRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Dialect dialect = manager.getDialect();
            final String sql;
            if (dialect == Dialect.POSTGRESQL) {
                sql = """
                        CREATE TABLE IF NOT EXISTS {player_addresses} (
                            id BIGSERIAL PRIMARY KEY,
                            player_uuid VARCHAR(36),
                            nickname VARCHAR(20) NOT NULL,
                            lower_nickname VARCHAR(20) NOT NULL,
                            host_address VARCHAR(50) NOT NULL,
                            last_usage TIMESTAMP NOT NULL
                        );
                        """;
            } else if (dialect == Dialect.SQLITE) {
                sql = """
                        CREATE TABLE IF NOT EXISTS {player_addresses} (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid VARCHAR(36),
                            nickname VARCHAR(20) NOT NULL,
                            lower_nickname VARCHAR(20) NOT NULL,
                            host_address VARCHAR(50) NOT NULL,
                            last_usage TIMESTAMP NOT NULL
                        );
                        """;
            } else {
                sql = """
                        CREATE TABLE IF NOT EXISTS {player_addresses} (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            player_uuid VARCHAR(36),
                            nickname VARCHAR(20) NOT NULL,
                            lower_nickname VARCHAR(20) NOT NULL,
                            host_address VARCHAR(50) NOT NULL,
                            last_usage DATETIME NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """;
            }
            try (PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
                statement.execute();
            }
            manager.ensureIndex(connection, "player_addresses", "host_address");
            manager.ensureIndex(connection, "player_addresses", "player_uuid");
            manager.ensureIndex(connection, "player_addresses", "lower_nickname");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void record(PlayerRef player, String address, LocalDateTime at) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final int updated;
            try (PreparedStatement update = connection.prepareStatement(manager.sql(
                    "UPDATE {player_addresses} SET player_uuid = ?, nickname = ?, lower_nickname = ?, last_usage = ? WHERE host_address = ? AND "
                            + IdentitySql.of(player, "player_uuid", "lower_nickname")))) {
                update.setString(1, player.uuid() == null ? null : player.uuid().toString());
                update.setString(2, player.name());
                update.setString(3, player.lowerName());
                update.setTimestamp(4, SqlTime.of(at));
                update.setString(5, address);
                IdentitySql.bind(update, 6, player);
                updated = update.executeUpdate();
            }
            if (updated > 1) dropDuplicates(connection, player, address);
            if (updated > 0) return;
            try (PreparedStatement insert = connection.prepareStatement(manager.sql(INSERT))) {
                bind(insert, player.uuid(), player.name(), address, at);
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * A row copied from 1.x (name only) and a row of the account on the same address became one account's: one stays.
     */
    private void dropDuplicates(Connection connection, PlayerRef player, String address) throws SQLException {
        final List<Long> ids = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(manager.sql("SELECT id FROM {player_addresses} WHERE host_address = ? AND "
                + IdentitySql.of(player, "player_uuid", "lower_nickname") + " ORDER BY id"))) {
            select.setString(1, address);
            IdentitySql.bind(select, 2, player);
            try (ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) ids.add(resultSet.getLong(1));
            }
        }
        try (PreparedStatement delete = connection.prepareStatement(manager.sql("DELETE FROM {player_addresses} WHERE id = ?"))) {
            for (Long id : ids.subList(1, ids.size())) {
                delete.setLong(1, id);
                delete.addBatch();
            }
            delete.executeBatch();
        }
    }

    @Override
    public int countOf(PlayerRef player) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT COUNT(*) FROM {player_addresses} WHERE "
                     + IdentitySql.of(player, "player_uuid", "lower_nickname")))) {
            IdentitySql.bind(statement, 1, player);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    private static void bind(PreparedStatement statement, UUID uuid, String nickname, String address, LocalDateTime at) throws SQLException {
        statement.setString(1, uuid == null ? null : uuid.toString());
        statement.setString(2, nickname);
        statement.setString(3, nickname.toLowerCase(Locale.ROOT));
        statement.setString(4, address);
        statement.setTimestamp(5, SqlTime.of(at));
    }

    @Override
    public List<PlayerAddress> findAllOf(PlayerRef player) {
        return query("SELECT * FROM {player_addresses} WHERE " + IdentitySql.of(player, "player_uuid", "lower_nickname")
                + " ORDER BY last_usage DESC, id DESC", statement -> IdentitySql.bind(statement, 1, player));
    }

    @Override
    public List<PlayerAddress> findByAddress(String address) {
        return query("SELECT * FROM {player_addresses} WHERE host_address = ? ORDER BY last_usage DESC, id DESC",
                statement -> statement.setString(1, address));
    }

    @Override
    public int deleteAllOf(PlayerRef player) {
        return execute("DELETE FROM {player_addresses} WHERE " + IdentitySql.of(player, "player_uuid", "lower_nickname"),
                statement -> IdentitySql.bind(statement, 1, player));
    }

    @Override
    public int deleteUnusedBefore(LocalDateTime cutoff) {
        return execute("DELETE FROM {player_addresses} WHERE last_usage < ?", statement -> statement.setTimestamp(1, SqlTime.of(cutoff)));
    }

    @Override
    public int fillUuid(PlayerRef account) {
        return execute("UPDATE {player_addresses} SET player_uuid = ? WHERE player_uuid IS NULL AND lower_nickname = ?", statement -> {
            statement.setString(1, account.uuid().toString());
            statement.setString(2, account.lowerName());
        });
    }

    @Override
    public Optional<PlayerAddress> findSame(PlayerAddress address) {
        final List<PlayerAddress> found = address.uuid() != null
                ? query("SELECT * FROM {player_addresses} WHERE player_uuid = ? AND host_address = ?", statement -> {
            statement.setString(1, address.uuid().toString());
            statement.setString(2, address.hostAddress());
        })
                : query("SELECT * FROM {player_addresses} WHERE player_uuid IS NULL AND lower_nickname = ? AND host_address = ?", statement -> {
            statement.setString(1, address.nickname().toLowerCase(Locale.ROOT));
            statement.setString(2, address.hostAddress());
        });
        return found.stream().findFirst();
    }

    @Override
    public void save(PlayerAddress address) {
        final Optional<PlayerAddress> stored = findSame(address);
        if (stored.isEmpty()) {
            execute(INSERT, statement -> bind(statement, address.uuid(), address.nickname(), address.hostAddress(), address.lastUsage()));
            return;
        }
        execute("UPDATE {player_addresses} SET nickname = ?, lower_nickname = ?, last_usage = ? WHERE id = ?", statement -> {
            statement.setString(1, address.nickname());
            statement.setString(2, address.nickname().toLowerCase(Locale.ROOT));
            statement.setTimestamp(3, SqlTime.of(address.lastUsage()));
            statement.setLong(4, stored.get().id());
        });
    }

    @Override
    public int migrateLegacy(Function<String, UUID> uuidOfName) {
        final String legacy = manager.table("access_addresses");
        final String migrating = legacy + "_migrating";
        try (Connection connection = manager.getDataSource().getConnection()) {
            if (SchemaUtil.tableExists(connection, legacy)) {
                if (!LegacyTables.hasColumnsOf(connection, legacy, "access_addresses")) {
                    logger.warn("Table {} does not have the columns of Judicator 1.x: its IP history was not copied.", legacy);
                    return 0;
                }
                // the claim: only one of the servers sharing the database renames it, and that one copies it
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE " + legacy + " RENAME TO " + migrating);
                } catch (SQLException e) {
                    if (!SchemaUtil.tableExists(connection, legacy)) return 0;
                    throw e;
                }
            } else if (!SchemaUtil.tableExists(connection, migrating)) {
                return 0;
            }
            // a copy that was committed before the last rename failed is not made again
            final int copied = SchemaUtil.countRows(connection, manager.table("player_addresses")) > 0 ? 0 : copy(connection, migrating, uuidOfName);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + migrating + " RENAME TO " + backupName(connection, legacy));
            }
            logger.info("Copied the IP history of {} ({} account-address pairs) to {}; the old table is kept as a backup.",
                    legacy, copied, manager.table("player_addresses"));
            return copied;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not copy the IP history of Judicator 1.x (" + e.getMessage()
                    + "); fix the cause and start again.", e);
        }
    }

    private int copy(Connection connection, String legacy, Function<String, UUID> uuidOfName) throws SQLException {
        final boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        int copied = 0;
        try (Statement select = connection.createStatement();
             ResultSet rows = select.executeQuery("SELECT host_address, accounts, last_usage FROM " + legacy);
             PreparedStatement insert = connection.prepareStatement(manager.sql(INSERT))) {
            while (rows.next()) {
                final String host = rows.getString("host_address");
                final String accounts = rows.getString("accounts");
                final LocalDateTime lastUsage = SqlTime.read(rows, "last_usage");
                if (host == null || accounts == null || lastUsage == null) continue;
                for (String name : Arrays.stream(accounts.split(",")).map(String::trim).filter(n -> !n.isEmpty()).distinct().toList()) {
                    if (name.length() > 20) continue;
                    bind(insert, uuidOfName.apply(name), name, host, lastUsage);
                    insert.addBatch();
                    copied++;
                }
            }
            insert.executeBatch();
            connection.commit();
            return copied;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String backupName(Connection connection, String legacy) throws SQLException {
        final String backup = legacy + "_old";
        return SchemaUtil.tableExists(connection, backup) ? backup + "_" + System.currentTimeMillis() / 1000 : backup;
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private List<PlayerAddress> query(String sql, Binder binder) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<PlayerAddress> addresses = new ArrayList<>();
                while (resultSet.next()) addresses.add(load(resultSet));
                return addresses;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    private int execute(String sql, Binder binder) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    private static PlayerAddress load(ResultSet rs) throws SQLException {
        final String uuid = rs.getString("player_uuid");
        return new PlayerAddress(rs.getLong("id"), uuid == null || uuid.isBlank() ? null : UUID.fromString(uuid),
                rs.getString("nickname"), rs.getString("host_address"), SqlTime.read(rs, "last_usage"));
    }

}
