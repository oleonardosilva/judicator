package com.elless.judicator.commons.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static com.elless.judicator.testing.FakeConfig.map;
import static com.elless.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spigot/Paper before 1.16.5 has no {@code libraries:}: the driver is downloaded and loaded in a class loader of its
 * own, which only sees the JDK — never the old SQLite driver those servers bundle.
 */
class IsolatedDriverTest {

    @TempDir
    Path folder;

    private URLClassLoader drivers;
    private RelationalDBManager manager;

    @BeforeEach
    void setUp() throws Exception {
        final URL sqliteJar = org.sqlite.JDBC.class.getProtectionDomain().getCodeSource().getLocation();
        drivers = new URLClassLoader(new URL[]{sqliteJar}, ClassLoader.getPlatformClassLoader());
        manager = new RelationalDBManager(drivers);
        manager.initialize(of(map("database", map("type", "sqlite", "file", "isolated.db"))), folder, NOPLogger.NOP_LOGGER);
    }

    @AfterEach
    void tearDown() throws Exception {
        manager.shutdown();
        drivers.close();
    }

    @Test
    void connectionsComeFromTheDriverOfTheIsolatedClassLoader() throws Exception {
        assertNotNull(manager.getDataSource(), "the pool was not created");
        try (Connection connection = manager.getDataSource().getConnection()) {
            assertSame(drivers, connection.unwrap(Connection.class).getClass().getClassLoader());
            assertEquals(Dialect.SQLITE, manager.getDialect());
        }
    }

    @Test
    void theSqliteSettingsReachTheDriver() throws Exception {
        try (Connection connection = manager.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet mode = statement.executeQuery("PRAGMA journal_mode")) {
            assertTrue(mode.next());
            assertEquals("wal", mode.getString(1).toLowerCase());
        }
    }

    @Test
    void tablesAreWrittenAndReadBack() throws Exception {
        try (Connection connection = manager.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + manager.table("probe") + " (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("INSERT INTO " + manager.table("probe") + " (name) VALUES ('Steve')");
            try (ResultSet rows = statement.executeQuery("SELECT name FROM " + manager.table("probe"))) {
                assertTrue(rows.next());
                assertEquals("Steve", rows.getString(1));
            }
        }
    }
}
