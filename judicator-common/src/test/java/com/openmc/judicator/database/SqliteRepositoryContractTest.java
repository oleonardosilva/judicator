package com.openmc.judicator.database;

import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.LegacyTables;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.dao.AccessAddressRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.sync.repository.dao.SyncRelationalDAO;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.nio.file.Path;

import static com.openmc.judicator.testing.FakeConfig.map;
import static com.openmc.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        final NoteRelationalDAO notes = new NoteRelationalDAO(manager, LOGGER);
        punishments.initialize();
        warns.initialize();
        addresses.initialize();
        levels.initialize();
        notes.initialize();
        final SyncRelationalDAO sync = new SyncRelationalDAO(manager, LOGGER);
        sync.initialize();
        return new Store(punishments, warns, addresses, levels, notes, sync, manager::shutdown);
    }

    @Test
    void aPunishmentsTableOfVersion100_isRenamedGetsTheNewColumnsAndKeepsItsRows() throws Exception {
        final Path oldFolder = Files.createDirectories(folder.resolve("old"));
        final RelationalDBManager old = new RelationalDBManager();
        old.initialize(of(map("database", map("type", "sqlite", "file", "old.db"))), oldFolder, LOGGER);
        try {
            try (Connection connection = old.getDataSource().getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE punishments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid VARCHAR(36), reason VARCHAR(255) NOT NULL,
                            punisher VARCHAR(20) NOT NULL, nickname VARCHAR(20), ip_address VARCHAR(45), lower_nickname VARCHAR(20),
                            started_at TIMESTAMP NOT NULL, finish_at TIMESTAMP, revoked BOOLEAN NOT NULL, evidences VARCHAR(255),
                            type VARCHAR(20) NOT NULL, permanent BOOLEAN NOT NULL, revoked_reason VARCHAR(100))
                        """);
                statement.executeUpdate("INSERT INTO punishments (reason, punisher, nickname, lower_nickname, started_at, revoked, "
                        + "evidences, type, permanent) VALUES ('spam', 'Mod', 'Steve', 'steve', 0, false, '', 'MUTE', true)");
            }

            LegacyTables.migrate(old, LOGGER);
            final PunishmentRelationalDAO dao = new PunishmentRelationalDAO(old, LOGGER);
            dao.initialize();
            dao.initialize(); // every startup runs it again

            final Punishment existing = dao.findAllByUsername("steve").get(0);
            assertFalse(existing.isSoft());
            assertFalse(existing.isOnlineOnly());
            assertEquals(null, existing.getPausedRemaining());
            final Punishment soft = Punishment.create(null, "x", "Mod", "Alex", null, LocalDateTime.now().withNano(0), null,
                    List.of(), PunishType.MUTE);
            soft.setSoft(true);
            dao.save(soft);
            assertTrue(dao.findById(soft.getId()).orElseThrow().isSoft());
        } finally {
            old.shutdown();
        }
    }

    @Test
    void usesTheSqliteFileInThePluginFolder() {
        assertEquals(Dialect.SQLITE, manager.getDialect());
        assertTrue(Files.exists(folder.resolve("test.db")));
    }
}
