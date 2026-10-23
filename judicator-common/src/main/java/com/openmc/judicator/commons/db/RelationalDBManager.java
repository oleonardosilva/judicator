package com.openmc.judicator.commons.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class RelationalDBManager {

    private HikariDataSource dataSource;
    private Dialect dialect;

    public RelationalDBManager() {
    }

    public void initialize(ConfigurationNode config, Path dataDirectory, Logger logger) {
        try {
            config = config.node("database");
            final Dialect configured = Dialect.fromConfig(config.node("type").getString("postgres"));

            if (dialect != null && dialect != configured && dataSource != null && !dataSource.isClosed()) {
                // Tables are created on startup only, so switching backends on /judicator reload would leave the
                // new database without them. Keep the running pool.
                logger.warn("database.type changed from {} to {}: restart the server to switch databases.", dialect, configured);
                return;
            }

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            final String driverClass = configured.getDriverClass();
            try {
                Class.forName(driverClass);
                logger.info("Database driver loaded successfully: {}", driverClass);
            } catch (ClassNotFoundException e) {
                logger.error("Database driver not found: {}. Make sure dependencies are loaded first.", driverClass);
                throw new RuntimeException("Database driver not available", e);
            }

            final HikariConfig hikari = new HikariConfig();
            hikari.setDriverClassName(driverClass);
            hikari.setPoolName("Judicator");

            if (configured == Dialect.SQLITE) {
                final Path file = dataDirectory.resolve(config.node("file").getString("judicator.db")).normalize();
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                hikari.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
                // One file, one writer at a time: WAL lets reads run during a write and busy_timeout makes a second
                // writer wait instead of failing with SQLITE_BUSY.
                hikari.addDataSourceProperty("journal_mode", "WAL");
                hikari.addDataSourceProperty("busy_timeout", "10000");
                hikari.setMaximumPoolSize(4);
                hikari.setMinimumIdle(1);
                logger.info("Using SQLite database file {}", file.toAbsolutePath());
            } else {
                final String host = config.node("host").getString("localhost");
                final String port = config.node("port").getString("").trim();
                final String database = config.node("database").getString("postgres");
                hikari.setJdbcUrl((configured == Dialect.POSTGRESQL ? "jdbc:postgresql://" : "jdbc:mysql://")
                        + host + ":" + (port.isEmpty() ? configured.getDefaultPort() : port) + "/" + database);
                hikari.setUsername(config.node("user").getString("postgres"));
                hikari.setPassword(config.node("password").getString("password"));
                final ConfigurationNode pool = config.node("pool");
                hikari.setMaximumPoolSize(Math.max(1, pool.node("maximum-size").getInt(10)));
                hikari.setMinimumIdle(Math.max(0, Math.min(hikari.getMaximumPoolSize(), pool.node("minimum-idle").getInt(2))));
                hikari.setConnectionTimeout(TimeUnit.SECONDS.toMillis(Math.max(1, pool.node("connection-timeout").getLong(30))));
                // Passed to the driver as connection properties, e.g. useSSL for MySQL or sslmode for PostgreSQL.
                config.node("properties").childrenMap().forEach((key, value) -> {
                    if (value.getString() != null) hikari.addDataSourceProperty(String.valueOf(key), value.getString());
                });
            }

            this.dataSource = new HikariDataSource(hikari);
            this.dialect = configured;
        } catch (Exception e) {
            logger.error("Database initialization failed, verify your data.yml file.");
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Reads {@code database.type} from {@code data.yml} before the plugin is enabled, so a platform can fetch only
     * the driver it needs. A missing or unreadable file means the default (PostgreSQL).
     */
    public static Dialect readConfiguredDialect(Path dataDirectory) {
        final Path file = dataDirectory.resolve("data.yml");
        if (!Files.exists(file)) return Dialect.POSTGRESQL;
        try {
            return Dialect.fromConfig(YamlConfigurationLoader.builder().path(file).build().load()
                    .node("database", "type").getString("postgres"));
        } catch (Exception e) {
            return Dialect.POSTGRESQL;
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * @return the backend in use; PostgreSQL until {@link #initialize} succeeds
     */
    public Dialect getDialect() {
        return dialect == null ? Dialect.POSTGRESQL : dialect;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
