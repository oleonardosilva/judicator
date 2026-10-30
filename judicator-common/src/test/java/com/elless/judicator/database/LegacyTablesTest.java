package com.elless.judicator.database;

import com.elless.judicator.commons.db.LegacyTables;
import com.elless.judicator.commons.db.RelationalDBManager;
import com.elless.judicator.notes.Note;
import com.elless.judicator.notes.repository.dao.NoteRelationalDAO;
import com.elless.judicator.players.PlayerRef;
import com.elless.judicator.punish.PlayerAddress;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.data.repository.dao.PlayerAddressRelationalDAO;
import com.elless.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.elless.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.sync.repository.dao.SyncRelationalDAO;
import com.elless.judicator.testing.Version1Addresses;
import com.elless.judicator.warns.Warn;
import com.elless.judicator.warns.repository.dao.WarnRelationalDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.elless.judicator.testing.FakeConfig.map;
import static com.elless.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upgrading from 1.x: the tables it created without a prefix are renamed to {@code judicator_*} on the first start.
 * A database of 1.x is written here by the DAOs with {@code table-prefix: ''}, which gives the names 1.x used.
 */
class LegacyTablesTest {

    private static final Logger LOGGER = NOPLogger.NOP_LOGGER;
    private static final List<String> TABLES = List.of("punishments", "warns", "notes", "player_levels", "sync_messages", "access_addresses");

    @TempDir
    Path folder;

    private final List<RelationalDBManager> opened = new ArrayList<>();
    private final UUID steve = UUID.randomUUID();
    private final LocalDateTime start = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.SECONDS);

    @AfterEach
    void close() {
        opened.forEach(RelationalDBManager::shutdown);
    }

    private RelationalDBManager open(Object prefix) {
        final RelationalDBManager manager = new RelationalDBManager();
        final java.util.Map<String, Object> database = new java.util.LinkedHashMap<>(map("type", "sqlite", "file", "judicator.db"));
        if (prefix != null) database.put("table-prefix", prefix);
        manager.initialize(of(map("database", database)), folder, LOGGER);
        opened.add(manager);
        return manager;
    }

    private void writeVersion1Database() {
        final RelationalDBManager v1 = open("");
        final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(v1, LOGGER);
        final WarnRelationalDAO warns = new WarnRelationalDAO(v1, LOGGER);
        final NoteRelationalDAO notes = new NoteRelationalDAO(v1, LOGGER);
        final PlayerLevelRelationalDAO levels = new PlayerLevelRelationalDAO(v1, LOGGER);
        final SyncRelationalDAO sync = new SyncRelationalDAO(v1, LOGGER);
        punishments.initialize();
        warns.initialize();
        notes.initialize();
        levels.initialize();
        sync.initialize();
        punishments.save(Punishment.create(steve, "x-ray", "Mod", "Steve", null, start, null, List.of(), PunishType.BAN));
        warns.save(Warn.create(steve, "caps", "Mod", "Steve", start, null));
        notes.save(new Note(null, steve, "Steve", "Mod", "watch him", start));
        levels.saveLevel("Steve", steve, 5);
        sync.insert("server-a", "PUNISHED", 1);
        Version1Addresses.add(v1, "10.0.0.1", "steve", start);
        v1.shutdown();
    }

    private Daos daosOf(RelationalDBManager manager) {
        final Daos daos = new Daos(new PunishmentRelationalDAO(manager, LOGGER), new WarnRelationalDAO(manager, LOGGER),
                new NoteRelationalDAO(manager, LOGGER), new PlayerLevelRelationalDAO(manager, LOGGER),
                new SyncRelationalDAO(manager, LOGGER), new PlayerAddressRelationalDAO(manager, LOGGER));
        daos.punishments.initialize();
        daos.warns.initialize();
        daos.notes.initialize();
        daos.levels.initialize();
        daos.sync.initialize();
        daos.addresses.initialize();
        daos.addresses.migrateLegacy(name -> null);
        return daos;
    }

    private record Daos(PunishmentRelationalDAO punishments, WarnRelationalDAO warns, NoteRelationalDAO notes,
                        PlayerLevelRelationalDAO levels, SyncRelationalDAO sync, PlayerAddressRelationalDAO addresses) {
    }

    /**
     * Starts as the plugin does: the migration, then each DAO creates what is missing.
     */
    private Daos startVersion2() {
        final RelationalDBManager manager = open(null);
        LegacyTables.migrate(manager, LOGGER);
        return daosOf(manager);
    }

    private static List<String> names(RelationalDBManager manager, String type) throws SQLException {
        final List<String> names = new ArrayList<>();
        try (Connection connection = manager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type = ? AND name NOT LIKE 'sqlite_%'")) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) names.add(resultSet.getString(1));
            }
        }
        return names;
    }

    private void assertVersion1DataIsThere(Daos daos) {
        assertEquals("x-ray", daos.punishments.findAllByUsername("steve").get(0).getReason().orElseThrow());
        assertEquals("caps", daos.warns.findAllByUsername("steve").get(0).getReason());
        assertEquals(1, daos.notes.countOf(PlayerRef.byName("steve", null)));
        assertEquals(5, daos.levels.findLevel(PlayerRef.byName("steve", null)).orElseThrow());
        assertEquals(1, daos.sync.maxId().orElseThrow());
        assertEquals(List.of("steve"), daos.addresses.findByAddress("10.0.0.1").stream().map(PlayerAddress::nickname).toList());
    }

    @Test
    void theTablesOfVersion1_areRenamedWithTheirRows() throws SQLException {
        writeVersion1Database();

        final Daos daos = startVersion2();

        assertVersion1DataIsThere(daos);
        final List<String> tables = names(opened.get(opened.size() - 1), "table");
        for (String table : TABLES) {
            // 2.0 copies the IP history into player_addresses and keeps the old table as a backup
            final String expected = table.equals("access_addresses") ? "judicator_access_addresses_old" : "judicator_" + table;
            assertTrue(tables.contains(expected), tables.toString());
            assertFalse(tables.contains(table), tables.toString());
        }
        assertTrue(tables.contains("judicator_player_addresses"), tables.toString());
    }

    @Test
    void theIndexesOfVersion1_areReplacedByPrefixedOnes() throws SQLException {
        writeVersion1Database();

        startVersion2();

        final List<String> indexes = names(opened.get(opened.size() - 1), "index");
        assertTrue(indexes.contains("idx_judicator_punishments_player_uuid"), indexes.toString());
        assertTrue(indexes.contains("idx_judicator_player_addresses_host_address"), indexes.toString());
        assertFalse(indexes.contains("idx_access_addresses_host_address"), indexes.toString());
        assertFalse(indexes.contains("idx_punishments_player_uuid"), indexes.toString());
        assertFalse(indexes.contains("idx_notes_lower_nickname"), indexes.toString());
    }

    @Test
    void aSecondStart_changesNothing() throws SQLException {
        writeVersion1Database();
        startVersion2().punishments.save(Punishment.create(steve, "again", "Mod", "Steve", null, start.plusDays(1), null,
                List.of(), PunishType.MUTE));

        final Daos daos = startVersion2();

        assertEquals(2, daos.punishments.findAllByUsername("steve").size());
        assertVersion1DataIsThere(daos);
    }

    @Test
    void aCopyLeftHalfDone_isFinishedOnTheNextStart() throws SQLException {
        writeVersion1Database();
        final RelationalDBManager manager = open(null);
        LegacyTables.migrate(manager, LOGGER);
        // a start that claimed the IP history of 1.x and stopped before copying it
        try (Connection connection = manager.getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE judicator_access_addresses RENAME TO judicator_access_addresses_migrating");
        }

        final Daos daos = daosOf(manager);

        assertEquals(List.of("steve"), daos.addresses.findByAddress("10.0.0.1").stream().map(PlayerAddress::nickname).toList());
        final List<String> tables = names(manager, "table");
        assertTrue(tables.contains("judicator_access_addresses_old"), tables.toString());
        assertFalse(tables.contains("judicator_access_addresses_migrating"), tables.toString());
        daos.addresses.migrateLegacy(name -> null);
        assertEquals(1, daos.addresses.findByAddress("10.0.0.1").size(), "copied once");
    }

    @Test
    void anotherPluginsTableWithTheSameName_isLeftAlone() throws SQLException {
        final RelationalDBManager other = open("");
        try (Connection connection = other.getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE punishments (id INTEGER PRIMARY KEY, offender TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO punishments (offender) VALUES ('Notch')");
        }
        other.shutdown();

        final Daos daos = startVersion2();
        daos.punishments.save(Punishment.create(steve, "x-ray", "Mod", "Steve", null, start, null, List.of(), PunishType.BAN));

        try (Connection connection = opened.get(opened.size() - 1).getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT offender FROM punishments")) {
            assertTrue(resultSet.next());
            assertEquals("Notch", resultSet.getString(1));
            assertFalse(resultSet.next());
        }
        assertEquals(1, daos.punishments.findAllByUsername("steve").size());
    }

    @Test
    void whenThePrefixedTableAlreadyExists_theOldOneIsNotTouched() throws SQLException {
        startVersion2().punishments.save(Punishment.create(steve, "new", "Mod", "Steve", null, start, null, List.of(), PunishType.BAN));
        writeVersion1Database();

        final Daos daos = startVersion2();

        assertEquals(List.of("new"), daos.punishments.findAllByUsername("steve").stream().map(p -> p.getReason().orElseThrow()).toList());
        assertTrue(names(opened.get(opened.size() - 1), "table").contains("punishments"));
    }

    @Test
    void withoutAPrefix_theNamesOfVersion1AreKept() throws SQLException {
        writeVersion1Database();
        final RelationalDBManager manager = open("");
        LegacyTables.migrate(manager, LOGGER);

        assertVersion1DataIsThere(daosOf(manager));
        assertFalse(names(manager, "table").contains("judicator_punishments"));
    }

    @Test
    void anInvalidPrefix_leavesTheDatabaseUnusedAndTheTablesAsTheyWere() throws SQLException {
        writeVersion1Database();

        for (String invalid : List.of("x; DROP TABLE punishments", "2_", "my-server_", "a_prefix_far_too_long")) {
            final RelationalDBManager manager = open(invalid);
            assertEquals(null, manager.getDataSource(), invalid);
            LegacyTables.migrate(manager, LOGGER);
        }

        assertTrue(names(open(""), "table").contains("punishments"));
    }

    @Test
    void aMigrationThatCannotRun_stopsTheStartInsteadOfCreatingEmptyTables() {
        writeVersion1Database();
        final RelationalDBManager manager = open(null);
        manager.shutdown();

        assertThrows(IllegalStateException.class, () -> LegacyTables.migrate(manager, LOGGER));
    }

    @Test
    void aValidPrefix_isUsedInLowerCase() {
        final RelationalDBManager manager = open("Net2_");

        assertEquals("net2_punishments", manager.table("punishments"));
        assertEquals("SELECT * FROM net2_punishments WHERE id = ?", manager.sql("SELECT * FROM {punishments} WHERE id = ?"));
        assertEquals("idx_net2_warns_player_uuid", manager.index("warns", "player_uuid"));
    }

}
