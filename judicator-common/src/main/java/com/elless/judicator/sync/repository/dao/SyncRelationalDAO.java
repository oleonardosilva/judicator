package com.elless.judicator.sync.repository.dao;

import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.sync.SyncMessage;
import com.elless.judicator.sync.repository.SyncRepository;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public class SyncRelationalDAO implements SyncRepository {

    private final RelationalDBManager manager;
    private final Logger logger;

    public SyncRelationalDAO(RelationalDBManager manager, Logger logger) {
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
                        CREATE TABLE IF NOT EXISTS {sync_messages} (
                            id BIGSERIAL PRIMARY KEY,
                            sender VARCHAR(36) NOT NULL,
                            kind VARCHAR(16) NOT NULL,
                            punishment_id BIGINT NOT NULL
                        );
                        """;
            } else if (dialect == Dialect.SQLITE) {
                sql = """
                        CREATE TABLE IF NOT EXISTS {sync_messages} (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            sender VARCHAR(36) NOT NULL,
                            kind VARCHAR(16) NOT NULL,
                            punishment_id BIGINT NOT NULL
                        );
                        """;
            } else {
                sql = """
                        CREATE TABLE IF NOT EXISTS {sync_messages} (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            sender VARCHAR(36) NOT NULL,
                            kind VARCHAR(16) NOT NULL,
                            punishment_id BIGINT NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                        """;
            }
            try (PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
                statement.execute();
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void insert(String sender, String kind, long punishmentId) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     manager.sql("INSERT INTO {sync_messages} (sender, kind, punishment_id) VALUES (?, ?, ?)"))) {
            statement.setString(1, sender);
            statement.setString(2, kind);
            statement.setLong(3, punishmentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public OptionalLong maxId() {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT MAX(id) FROM {sync_messages}"));
             ResultSet resultSet = statement.executeQuery()) {
            return OptionalLong.of(resultSet.next() ? resultSet.getLong(1) : 0);
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return OptionalLong.empty();
        }
    }

    @Override
    public List<SyncMessage> findAfter(long afterId, int limit) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     manager.sql("SELECT id, sender, kind, punishment_id FROM {sync_messages} WHERE id > ? ORDER BY id LIMIT ?"))) {
            statement.setLong(1, afterId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<SyncMessage> messages = new ArrayList<>();
                while (resultSet.next()) {
                    messages.add(new SyncMessage(resultSet.getLong("id"), resultSet.getString("sender"),
                            resultSet.getString("kind"), resultSet.getLong("punishment_id")));
                }
                return messages;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public int deleteUpTo(long id) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("DELETE FROM {sync_messages} WHERE id <= ?"))) {
            statement.setLong(1, id);
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return 0;
        }
    }

}
