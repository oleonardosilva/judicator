package com.openmc.judicator.database;

import com.openmc.judicator.players.PlayerRef;
import com.openmc.judicator.players.repository.dao.PlayerRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PlayerAddressRelationalDAO;
import com.openmc.judicator.commons.db.Dialect;
import com.openmc.judicator.commons.db.LegacyTables;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.reports.repository.dao.ReportRelationalDAO;
import com.openmc.judicator.sync.repository.dao.SyncRelationalDAO;
import com.openmc.judicator.warns.Warn;
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
import java.util.UUID;

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
        final PlayerAddressRelationalDAO addresses = new PlayerAddressRelationalDAO(manager, LOGGER);
        final PlayerLevelRelationalDAO levels = new PlayerLevelRelationalDAO(manager, LOGGER);
        final NoteRelationalDAO notes = new NoteRelationalDAO(manager, LOGGER);
        punishments.initialize();
        warns.initialize();
        addresses.initialize();
        levels.initialize();
        notes.initialize();
        final SyncRelationalDAO sync = new SyncRelationalDAO(manager, LOGGER);
        sync.initialize();
        final PlayerRelationalDAO players = new PlayerRelationalDAO(manager, LOGGER);
        players.initialize();
        final ReportRelationalDAO reports = new ReportRelationalDAO(manager, LOGGER);
        reports.initialize();
        return new Store(punishments, warns, addresses, levels, notes, sync, players, reports, manager::shutdown);
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
    void theFirstStartOf2_0_fillsThePlayersTableWithTheAccountsTheRecordsKnow() {
        final LocalDateTime now = LocalDateTime.now().withNano(0);
        final UUID steve = UUID.randomUUID();
        final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(manager, LOGGER);
        final Punishment older = Punishment.create(steve, "x", "Mod", "Steve", null, now.minusDays(5), null, List.of(), PunishType.BAN);
        punishments.save(older);
        punishments.save(Punishment.create(steve, "y", "Mod", "Alex", null, now.minusDays(1), null, List.of(), PunishType.MUTE));
        final WarnRelationalDAO warns = new WarnRelationalDAO(manager, LOGGER);
        // a warn of a name nobody had seen holds the offline-mode UUID of the name: not an account
        warns.save(Warn.create(PlayerRef.offlineUuid("Ghost"), "z", "Mod", "Ghost", now, null));
        final PlayerRelationalDAO players = new PlayerRelationalDAO(manager, LOGGER);
        players.initialize();

        final PlayerLevelRelationalDAO levels = new PlayerLevelRelationalDAO(manager, LOGGER);
        levels.saveLevel("Alex", steve, 10); // newer than the punishments, but only in lower case
        final UUID staffOnly = UUID.randomUUID();
        levels.saveLevel("Helper", staffOnly, 5);

        assertEquals(2, players.seedIfEmpty());
        assertEquals(0, players.seedIfEmpty(), "only an empty table is filled");
        assertEquals("Alex", players.findByUuid(steve).orElseThrow().name(), "the most recent name of the account, as it was written");
        assertEquals("helper", players.findByUuid(staffOnly).orElseThrow().name(), "a staff level alone gives the lower-case name");
        assertTrue(players.findLatestByName("Ghost").isEmpty());
    }

    @Test
    void usesTheSqliteFileInThePluginFolder() {
        assertEquals(Dialect.SQLITE, manager.getDialect());
        assertTrue(Files.exists(folder.resolve("test.db")));
    }
}
