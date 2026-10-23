package com.openmc.judicator.database;

import com.openmc.judicator.commons.db.LegacyTables;
import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.dao.AccessAddressRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.hierarchy.PlayerLevelRelationalDAO;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.sync.repository.dao.SyncRelationalDAO;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;
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

import static com.openmc.judicator.testing.FakeConfig.map;
import static com.openmc.judicator.testing.FakeConfig.of;
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
        final AccessAddressRelationalDAO addresses = new AccessAddressRelationalDAO(v1, LOGGER);
        punishments.initialize();
        warns.initialize();
        notes.initialize();
        levels.initialize();
        sync.initialize();
        addresses.initialize();
        punishments.save(Punishment.create(steve, "x-ray", "Mod", "Steve", null, start, null, List.of(), PunishType.BAN));
        warns.save(Warn.create(steve, "caps", "Mod", "Steve", start, null));
        notes.save(new Note(null, steve, "Steve", "Mod", "watch him", start));
        levels.saveLevel("Steve", steve, 5);
        sync.insert("server-a", "PUNISHED", 1);
        addresses.save(new AccessAddress(null, "10.0.0.1", List.of("steve"), start));
        v1.shutdown();
    }

    private Daos daosOf(RelationalDBManager manager) {
        final Daos daos = new Daos(new PunishmentRelationalDAO(manager, LOGGER), new WarnRelationalDAO(manager, LOGGER),
                new NoteRelationalDAO(manager, LOGGER), new PlayerLevelRelationalDAO(manager, LOGGER),
                new SyncRelationalDAO(manager, LOGGER), new AccessAddressRelationalDAO(manager, LOGGER));
        daos.punishments.initialize();
        daos.warns.initialize();
        daos.notes.initialize();
        daos.levels.initialize();
        daos.sync.initialize();
        daos.addresses.initialize();
        return daos;
    }

    private record Daos(PunishmentRelationalDAO punishments, WarnRelationalDAO warns, NoteRelationalDAO notes,
                        PlayerLevelRelationalDAO levels, SyncRelationalDAO sync, AccessAddressRelationalDAO addresses) {
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
        assertEquals(1, daos.notes.countByUsername("steve"));
        assertEquals(5, daos.levels.findLevel("steve").orElseThrow());
        assertEquals(1, daos.sync.maxId().orElseThrow());
        assertTrue(daos.addresses.findByIp("10.0.0.1").orElseThrow().contains("steve"));
    }

    @Test
    void theTablesOfVersion1_areRenamedWithTheirRows() throws SQLException {
        writeVersion1Database();

        final Daos daos = startVersion2();

        assertVersion1DataIsThere(daos);
        final List<String> tables = names(opened.get(opened.size() - 1), "table");
        for (String table : TABLES) {
            assertTrue(tables.contains("judicator_" + table), tables.toString());
            assertFalse(tables.contains(table), tables.toString());
        }
    }

    @Test
    void theIndexesOfVersion1_areReplacedByPrefixedOnes() throws SQLException {
        writeVersion1Database();

        startVersion2();

        final List<String> indexes = names(opened.get(opened.size() - 1), "index");
        assertTrue(indexes.contains("idx_judicator_punishments_player_uuid"), indexes.toString());
        assertTrue(indexes.contains("idx_judicator_access_addresses_host_address"), indexes.toString());
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
