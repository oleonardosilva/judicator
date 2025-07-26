package com.openmc.plugin.judicator.punish.data.repository.dao;

import com.openmc.plugin.judicator.commons.db.RelationalDBManager;
import com.openmc.plugin.judicator.commons.db.SchemaUtil;
import com.openmc.plugin.judicator.punish.AccessAddress;
import com.openmc.plugin.judicator.punish.data.repository.AccessAddressRepository;
import org.slf4j.Logger;

import java.sql.*;
import java.time.ZoneOffset;
import java.util.Arrays;
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
            final String dbProduct = connection.getMetaData().getDatabaseProductName().toLowerCase();
            final PreparedStatement statement;
            if (dbProduct.contains("postgresql")) {
                statement = connection.prepareStatement(
                        """
                                CREATE TABLE IF NOT EXISTS access_addresses (
                                    id BIGSERIAL PRIMARY KEY,
                                    host_address VARCHAR(50) NOT NULL,
                                    accounts TEXT NOT NULL,
                                    last_usage TIMESTAMP NOT NULL
                                );
                                """
                );
            } else {
                statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS access_addresses (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            host_address VARCHAR(50) NOT NULL,
                            accounts TEXT NOT NULL,
                            last_usage DATETIME NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """);
            }
            statement.execute();

            SchemaUtil.ensureIndexExists(
                    connection,
                    "access_addresses",
                    "idx_access_addresses_host_address",
                    "CREATE INDEX idx_access_addresses_host_address ON access_addresses(host_address)"
            );
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public AccessAddress save(AccessAddress o) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final boolean isUpdate = o.getId() != null;
            final String sql = generateSaveSQL(isUpdate);
            final PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, o.getHostAddress());
            statement.setString(2, String.join(",", o.getAccounts()));
            statement.setTimestamp(3, Timestamp.from(o.getLastUsage().toInstant(ZoneOffset.UTC)));
            if (isUpdate) statement.setLong(4, o.getId());

            if (!isUpdate) {
                try (ResultSet rs = statement.getGeneratedKeys()) {
                    if (rs.next()) {
                        o.setId(rs.getLong(1));
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
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM access_addresses WHERE id = ?
                    """);
            statement.setString(1, id);
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
            final PreparedStatement st = connection.prepareStatement("DELETE FROM access_addresses WHERE id = ?");
            st.setString(1, id);
            st.execute();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private AccessAddress load(ResultSet rs) throws SQLException {
        final AccessAddress address = new AccessAddress();
        address.setId(rs.getLong("id"));
        address.setHostAddress(rs.getString("host_address"));
        address.setAccounts(Arrays.stream(rs.getString("accounts").split(",")).toList());
        address.setLastUsage(rs.getTimestamp("last_usage").toLocalDateTime());
        return address;
    }

    private String generateSaveSQL(boolean isUpdate) {
        if (isUpdate) {
            return """
                    UPDATE access_addresses SET host_address = ?, accounts = ?, last_usage = ? WHERE id = ?
                    """;
        }
        return """
                INSERT INTO access_addresses (host_address, accounts, last_usage) VALUES (?, ?, ?)
                """;
    }

    @Override
    public Optional<AccessAddress> findByIp(String ip) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM access_addresses WHERE host_address = ?
                    """);
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
        try (Connection connection = manager.getDataSource().getConnection()) {
            final PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM access_addresses WHERE accounts ILIKE ?
                    """);
            statement.setString(1, "%" + username + "%");
            final ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return Optional.of(load(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
