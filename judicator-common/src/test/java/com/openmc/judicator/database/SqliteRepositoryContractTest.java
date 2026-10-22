package com.openmc.judicator.database;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.punish.data.repository.dao.AccessAddressRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.openmc.judicator.testing.FakeConfig.map;
import static com.openmc.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relational DAOs (real SQL, real driver) against a SQLite file. Needs the sqlite-jdbc test dependency.
 */
class SqliteRepositoryContractTest extends RepositoryContract {

    private static final Logger LOGGER = NOPLogger.NOP_LOGGER;

    private RelationalDBManager manager;

    @Override
    protected Store open(Path folder) {
        manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", "sqlite", "file", "test.db"))), folder, LOGGER);
        final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(manager, LOGGER);
        final WarnRelationalDAO warns = new WarnRelationalDAO(manager, LOGGER);
        final AccessAddressRelationalDAO addresses = new AccessAddressRelationalDAO(manager, LOGGER);
        final PlayerLevelRelationalDAO levels = new PlayerLevelRelationalDAO(manager, LOGGER);
        punishments.initialize();
        warns.initialize();
        addresses.initialize();
        levels.initialize();
        return new Store(punishments, warns, addresses, levels, manager::shutdown);
    }

    @Test
    void usesTheSqliteFileInThePluginFolder() {
        assertEquals(Dialect.SQLITE, manager.getDialect());
        assertTrue(Files.exists(folder.resolve("test.db")));
    }
}
