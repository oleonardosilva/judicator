package com.openmc.judicator.commons.db;

import java.util.Locale;

/**
 * Database backends supported by Judicator, chosen with {@code database.type} in {@code data.yml}.
 */
public enum Dialect {

    POSTGRESQL("org.postgresql.Driver"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    SQLITE("org.sqlite.JDBC");

    private final String driverClass;

    Dialect(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getDriverClass() {
        return driverClass;
    }

    /**
     * @param type value of {@code database.type}. Missing means PostgreSQL (the historical default); any value other
     *             than postgres/postgresql/sqlite means MySQL, as in the versions before SQLite existed.
     */
    public static Dialect fromConfig(String type) {
        if (type == null) return POSTGRESQL;
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> POSTGRESQL;
            case "sqlite" -> SQLITE;
            default -> MYSQL;
        };
    }
}
