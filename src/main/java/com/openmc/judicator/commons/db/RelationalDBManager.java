package com.openmc.judicator.commons.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

import javax.sql.DataSource;

public class RelationalDBManager {

    private HikariDataSource dataSource;

    public RelationalDBManager() {
    }

    public void initialize(ConfigurationNode config, Logger logger) {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }

            config = config.node("database");
            final HikariConfig hikari = new HikariConfig();
            final String type = config.node("type").getString("postgres");
            final String host = config.node("host").getString("localhost");
            final String port = config.node("port").getString("5432");
            final String database = config.node("database").getString("postgres");
            final String username = config.node("user").getString("postgres");
            final String password = config.node("password").getString("password");
            final String jdbcUrl = type.equalsIgnoreCase("postgres") ?
                    "jdbc:postgresql://" + host + ":" + port + "/" + database :
                    "jdbc:mariadb://" + host + ":" + port + "/" + database +
                    "?useSSL=false&characterEncoding=UTF-8";

            hikari.setJdbcUrl(jdbcUrl);
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.setDriverClassName(
                    type.equalsIgnoreCase("postgres") ? "com.openmc.judicator.libs.postgresql.Driver" : "com.openmc.judicator.libs.mariadb.jdbc.Driver"
            );
            hikari.setMaximumPoolSize(10);
            hikari.setMinimumIdle(2);
            hikari.setPoolName("Judicator");
            this.dataSource = new HikariDataSource(hikari);
            logger.info("Database has been initialized successfully!");
        } catch (Exception e) {
            logger.error("Database initialization failed, verify your data.yml file.");
            logger.error(e.getMessage(), e);
        }
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