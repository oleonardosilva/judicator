package com.elless.judicator.jails.repository.dao;

import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.commons.db.SqlTime;
import com.elless.judicator.jails.EscapeRoute;
import com.elless.judicator.jails.Jail;
import com.elless.judicator.jails.JailArea;
import com.elless.judicator.jails.repository.JailRepository;
import com.elless.judicator.platform.Location;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code jails} table: one row per jail, its places as text ({@link Location#encode}, {@link JailArea#encode},
 * {@link EscapeRoute#encode}).
 */
public class JailRelationalDAO implements JailRepository {

    private static final String COLUMNS = "server = ?, spawn = ?, area = ?, release_point = ?, shared = ?, capacity = ?, escapes = ?";

    private final RelationalDBManager manager;
    private final Logger logger;

    public JailRelationalDAO(RelationalDBManager manager, Logger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        final String time = manager.getDialect() == Dialect.MYSQL ? "DATETIME" : "TIMESTAMP";
        final String engine = manager.getDialect() == Dialect.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        final String sql = """
                CREATE TABLE IF NOT EXISTS {jails} (
                    name VARCHAR(32) NOT NULL PRIMARY KEY,
                    server VARCHAR(32) NOT NULL,
                    spawn VARCHAR(160) NOT NULL,
                    area VARCHAR(160) NOT NULL,
                    release_point VARCHAR(160),
                    shared BOOLEAN NOT NULL,
                    capacity INT NOT NULL,
                    escapes VARCHAR(2000) NOT NULL,
                    created_by VARCHAR(20) NOT NULL,
                    created_at %s NOT NULL
                )%s;
                """.formatted(time, engine);
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql(sql))) {
            statement.execute();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public List<Jail> findAll() {
        final List<Jail> jails = new ArrayList<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("SELECT * FROM {jails} ORDER BY name"));
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final Optional<Jail> jail = read(resultSet);
                if (jail.isPresent()) jails.add(jail.get());
                else logger.warn("Jail '{}' has a spawn or an area that cannot be read: ignored.", resultSet.getString("name"));
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        return jails;
    }

    /**
     * The jail of the current row; empty when its spawn or area cannot be read.
     */
    public static Optional<Jail> read(ResultSet resultSet) throws SQLException {
        final Optional<Location> spawn = Location.decode(resultSet.getString("spawn"));
        final Optional<JailArea> area = JailArea.decode(resultSet.getString("area"));
        if (spawn.isEmpty() || area.isEmpty()) return Optional.empty();
        return Optional.of(new Jail(resultSet.getString("name"), resultSet.getString("server"), spawn.get(), area.get(),
                Location.decode(resultSet.getString("release_point")), resultSet.getBoolean("shared"),
                resultSet.getInt("capacity"), EscapeRoute.decode(resultSet.getString("escapes"))));
    }

    @Override
    public boolean save(Jail jail, String author) {
        try (Connection connection = manager.getDataSource().getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(manager.sql("UPDATE {jails} SET " + COLUMNS + " WHERE name = ?"))) {
                bind(update, jail);
                update.setString(8, jail.name());
                if (update.executeUpdate() > 0) return true;
            }
            try (PreparedStatement insert = connection.prepareStatement(manager.sql(
                    "INSERT INTO {jails} (server, spawn, area, release_point, shared, capacity, escapes, name, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))) {
                bind(insert, jail);
                insert.setString(8, jail.name());
                insert.setString(9, author.length() <= 20 ? author : author.substring(0, 20));
                insert.setTimestamp(10, SqlTime.of(LocalDateTime.now()));
                return insert.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    private static void bind(PreparedStatement statement, Jail jail) throws SQLException {
        statement.setString(1, jail.server());
        statement.setString(2, jail.spawn().encode());
        statement.setString(3, jail.area().encode());
        statement.setString(4, jail.release().map(Location::encode).orElse(null));
        statement.setBoolean(5, jail.shared());
        statement.setInt(6, jail.capacity());
        statement.setString(7, EscapeRoute.encode(jail.escapes()));
    }

    @Override
    public boolean delete(String name) {
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(manager.sql("DELETE FROM {jails} WHERE name = ?"))) {
            statement.setString(1, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }
}
