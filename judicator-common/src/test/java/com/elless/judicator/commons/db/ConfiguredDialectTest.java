package com.elless.judicator.commons.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguredDialectTest {

    @TempDir
    Path folder;

    @Test
    void firstStartUsesTheSqliteOfTheBundledDataFile() {
        // Velocity picks the drivers to download before data.yml is created: it must fetch the SQLite one.
        assertEquals(Dialect.SQLITE, RelationalDBManager.readConfiguredDialect(folder));
    }

    @Test
    void theTypeOfAnExistingDataFileWins() throws IOException {
        Files.writeString(folder.resolve("data.yml"), "database:\n  type: 'mysql'\n");
        assertEquals(Dialect.MYSQL, RelationalDBManager.readConfiguredDialect(folder));
    }

    @Test
    void anExistingDataFileWithoutTypeKeepsTheHistoricalDefault() throws IOException {
        Files.writeString(folder.resolve("data.yml"), "database:\n  host: 'localhost'\n");
        assertEquals(Dialect.POSTGRESQL, RelationalDBManager.readConfiguredDialect(folder));
    }
}
