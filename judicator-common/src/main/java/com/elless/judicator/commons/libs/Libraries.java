package com.elless.judicator.commons.libs;

import com.elless.judicator.commons.db.Dialect;
import com.elless.judicator.commons.libs.DependencyManager.Dependency;

/**
 * The libraries a platform may have to fetch itself, with the SHA-256 of each jar on Maven Central. Changing a
 * version means changing its hash too.
 */
public final class Libraries {

    public static final Dependency MYSQL = new Dependency("com.mysql:mysql-connector-j:9.3.0",
            "6c8e6692b521376d89bc5618c16cdeaf8c61854329f4fa25677ed08776c5bb76");
    public static final Dependency POSTGRESQL = new Dependency("org.postgresql:postgresql:42.7.7",
            "157963d60ae66d607e09466e8c0cdf8087e9cb20d0159899ffca96bca2528460");
    /** bundles native libraries (~13 MB): only fetched when {@code data.yml} asks for SQLite */
    public static final Dependency SQLITE = new Dependency("org.xerial:sqlite-jdbc:3.46.1.2",
            "f94550638a187cb8dd34391594303aa04ed3f168b31ab8026fe1e57db3a78c62");
    public static final Dependency CAFFEINE = new Dependency("com.github.ben-manes.caffeine:caffeine:3.1.8",
            "7dd15f9df1be238ffaa367ce6f556737a88031de4294dad18eef57c474ddf1d3");
    public static final Dependency HIKARI = new Dependency("com.zaxxer:HikariCP:6.3.0",
            "07c6344059af30a135144209c7c8bd666b8823124422ec859864d209d4ab7ca1");

    private Libraries() {
    }

    public static Dependency driverOf(Dialect dialect) {
        return switch (dialect) {
            case MYSQL -> MYSQL;
            case POSTGRESQL -> POSTGRESQL;
            case SQLITE -> SQLITE;
        };
    }
}
