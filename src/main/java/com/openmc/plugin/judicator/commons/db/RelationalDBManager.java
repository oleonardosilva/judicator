package com.openmc.plugin.judicator.commons.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.spongepowered.configurate.ConfigurationNode;

import javax.sql.DataSource;

public class RelationalDBManager {

    private final HikariDataSource dataSource;

    public RelationalDBManager(ConfigurationNode config) {
        config = config.node("database");
        final HikariConfig hikari = new HikariConfig();
        final String type = config.node("type").getString("postgres");
        final String host = config.node("host").getString("localhost");
        final String port = config.node("port").getString("5432");
        final String database = config.node("database").getString("postgres");
        final String username = config.node("user").getString("postgres");
        final String password = config.node("password").getString("postgres");
        String jdbcUrl = switch (type.toLowerCase()) {
            case "postgres" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            default -> "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&characterEncoding=UTF-8";
        };

        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setMaximumPoolSize(10);
        hikari.setMinimumIdle(2);
        hikari.setPoolName("Judicator");
        this.dataSource = new HikariDataSource(hikari);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}