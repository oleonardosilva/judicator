package com.openmc.judicator.punish.data.repository.dao;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.commons.db.SqlTime;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.data.repository.AccessAddressRepository;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class AccessAddressRelationalDAO implements AccessAddressRepository {

    private final RelationalDBManager manager;
    private final Logger logger;

    public AccessAddressRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final Dialect dialect = manager.getDialect();
            final PreparedStatement statement;
            if (dialect == Dialect.POSTGRESQL) {
                statement = connection.prepareStatement(
                        manager.sql("""
                                CREATE TABLE IF NOT EXISTS {access_addresses} (
                                    id BIGSERIAL PRIMARY KEY,
                                    host_address VARCHAR(50) NOT NULL,
                                    accounts TEXT NOT NULL,
                                    last_usage TIMESTAMP NOT NULL
                                );
                                """)
                );
            } else if (dialect == Dialect.SQLITE) {
                statement = connection.prepareStatement(manager.sql("""
                        CREATE TABLE IF NOT EXISTS {access_addresses} (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            host_address VARCHAR(50) NOT NULL,
                            accounts TEXT NOT NULL,
                            last_usage TIMESTAMP NOT NULL
                        );
                        """));
            } else {
                statement = connection.prepareStatement(manager.sql("""
                        CREATE TABLE IF NOT EXISTS {access_addresses} (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            host_address VARCHAR(50) NOT NULL,
                            accounts TEXT NOT NULL,
                            last_usage DATETIME NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """));
            }
            statement.execute();

            manager.ensureIndex(connection, "access_addresses", "host_address");
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public AccessAddress save(AccessAddress o) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean isUpdate = o.getId() != null;
            final String sql = generateSaveSQL(isUpdate);
            try (PreparedStatement statement = connection.prepareStatement(manager.sql(sql), Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, o.getHostAddress());
                statement.setString(2, String.join(",", o.getAccounts()));
                statement.setTimestamp(3, SqlTime.of(o.getLastUsage()));
                if (isUpdate) statement.setLong(4, o.getId());
                statement.executeUpdate();

                if (!isUpdate) {
                    try (ResultSet rs = statement.getGeneratedKeys()) {
                        if (rs.next()) {
                            o.setId(rs.getLong(1));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return o;
    }

    @Override
    public Optional<AccessAddress> findById(String id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {access_addresses} WHERE id = ?
                    """));
            // id is BIGINT: PostgreSQL does not compare it with a text parameter
            statement.setLong(1, Long.parseLong(id));
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
    public void deleteById(String id) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement st = connection.prepareStatement(manager.sql("DELETE FROM {access_addresses} WHERE id = ?"));
            st.setLong(1, Long.parseLong(id));
            st.execute();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public int deleteUnusedBefore(LocalDateTime cutoff) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("DELETE FROM {access_addresses} WHERE last_usage < ?"))) {
            statement.setTimestamp(1, SqlTime.of(cutoff));
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

    private AccessAddress load(ResultSet rs) throws SQLException {
        final AccessAddress address = new AccessAddress();
        address.setId(rs.getLong("id"));
        address.setHostAddress(rs.getString("host_address"));
        address.setAccounts(new ArrayList<>(Arrays.stream(rs.getString("accounts").split(",")).filter(a -> !a.isBlank()).toList()));
        address.setLastUsage(SqlTime.read(rs, "last_usage"));
        return address;
    }

    private String generateSaveSQL(boolean isUpdate) {
        if (isUpdate) {
            return """
                    UPDATE {access_addresses} SET host_address = ?, accounts = ?, last_usage = ? WHERE id = ?
                    """;
        }
        return """
                INSERT INTO {access_addresses} (host_address, accounts, last_usage) VALUES (?, ?, ?)
                """;
    }

    @Override
    public Optional<AccessAddress> findByIp(String ip) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(manager.sql("""
                    SELECT * FROM {access_addresses} WHERE host_address = ?
                    """));
            statement.setString(1, ip);
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
    public Optional<AccessAddress> findByUsername(String username) {
        return findAllByUsername(username).stream().findFirst();
    }

    @Override
    public List<AccessAddress> findAllByUsername(String username) {
        final String account = username.toLowerCase();
        final List<AccessAddress> addresses = new ArrayList<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("""
                     SELECT * FROM {access_addresses} WHERE LOWER(accounts) LIKE ? ORDER BY last_usage DESC
                     """))) {
            statement.setString(1, "%" + account + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final AccessAddress address = load(resultSet);
                    if (address.contains(account)) addresses.add(address);
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return addresses;
    }
}
