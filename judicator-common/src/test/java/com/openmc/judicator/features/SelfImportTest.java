package com.openmc.judicator.features;

import com.openmc.judicator.commons.db.RelationalDBManager;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.notes.repository.dao.NoteRelationalDAO;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.repository.dao.AccessAddressRelationalDAO;
import com.openmc.judicator.punish.data.repository.dao.PunishmentRelationalDAO;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.warns.Warn;
import com.openmc.judicator.warns.repository.dao.WarnRelationalDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.openmc.judicator.testing.FakeConfig.map;
import static com.openmc.judicator.testing.FakeConfig.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /judicator import judicator: copying another Judicator database (here a SQLite file written by the real DAOs) into
 * the current one, as when a server moves from SQLite to MySQL.
 */
class SelfImportTest {

    @TempDir
    Path folder;
    @TempDir
    Path oldServer;

    private TestServer server;
    private final LocalDateTime start = LocalDateTime.now().minusDays(10).truncatedTo(ChronoUnit.SECONDS);
    private final UUID steve = UUID.nameUUIDFromBytes("Steve".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void oldDatabase() throws Exception {
        final RelationalDBManager manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", "sqlite", "file", "old.db"))), oldServer, NOPLogger.NOP_LOGGER);
        try {
            final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            final WarnRelationalDAO warns = new WarnRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            final AccessAddressRelationalDAO addresses = new AccessAddressRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            punishments.initialize();
            warns.initialize();
            addresses.initialize();

            final Punishment ban = Punishment.create(steve, "x-ray", "Mod", "Steve", null, start, null,
                    new ArrayList<>(List.of("https://img.example/1", "https://img.example/2")), PunishType.BAN);
            punishments.save(ban);
            punishments.revoke(ban.getId(), "appeal accepted");
            punishments.save(Punishment.create(steve, "spam", "Helper", "Steve", null, start.plusDays(1), start.plusDays(40),
                    List.of(), PunishType.TEMPMUTE));
            punishments.save(Punishment.create(null, "proxy abuse", "Console", "10.9.9.9", "10.9.9.9", start.plusDays(2), null,
                    List.of(), PunishType.BAN));
            final Punishment kick = Punishment.create(steve, "calm down", "Mod", "Steve", null, start.plusDays(3), start.plusDays(3),
                    List.of(), PunishType.KICK);
            kick.setPermanent(false);
            punishments.save(kick);

            warns.save(Warn.create(steve, "caps", "Helper", "Steve", start.plusDays(4), null));

            addresses.save(new AccessAddress(null, "10.0.0.1", List.of("steve", "stevealt"), start.plusDays(5)));
            addresses.save(new AccessAddress(null, "10.0.0.7", List.of("alex"), start.plusDays(6)));
        } finally {
            manager.shutdown();
        }

        server = TestServer.in(folder).start();
        Files.writeString(folder.resolve("import.yml"), "judicator:\n  jdbc-url: 'jdbc:sqlite:"
                + oldServer.resolve("old.db").toString().replace("\\", "/").replace("'", "''") + "'\n", StandardCharsets.UTF_8);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void dryRunReportsWithoutWriting() {
        server.command(server.console, "judicator import judicator");

        assertTrue(server.console.received("judicator: 4 punishments and 1 warns to import, 0 already in Judicator."), server.console.messages().toString());
        assertTrue(server.console.received("IP addresses (for /alts): 2 new, 0 to merge"), server.console.messages().toString());
        assertTrue(server.punishments.all().isEmpty());
        assertTrue(server.addresses.findByIp("10.0.0.1").isEmpty());
    }

    @Test
    void copiesPunishmentsAsTheyWere() {
        server.command(server.console, "judicator import judicator confirm");

        final List<Punishment> all = server.punishments.all().stream().sorted(Comparator.comparing(Punishment::getStartedAt)).toList();
        assertEquals(4, all.size(), all.toString());

        final Punishment ban = all.get(0);
        assertEquals(PunishType.BAN, ban.getType());
        assertEquals(steve, ban.getPlayerUUID().orElseThrow());
        assertEquals("Mod", ban.getPunisher());
        assertEquals(start, ban.getStartedAt());
        assertTrue(ban.isRevoked());
        assertEquals("appeal accepted", ban.getRevokedReason().orElseThrow());
        assertEquals(List.of("https://img.example/1", "https://img.example/2"), ban.getEvidences());

        assertEquals(PunishType.TEMPMUTE, all.get(1).getType());
        assertEquals(start.plusDays(40), all.get(1).getFinishAt().orElseThrow());
        assertEquals("10.9.9.9", all.get(2).getIpAddress().orElseThrow());
        assertEquals(PunishType.KICK, all.get(3).getType());

        assertEquals("caps", server.warns.findAllByUsername("steve").get(0).getReason());
        assertTrue(server.console.received("Imported 4 punishments and 1 warns from judicator and updated 2 IP addresses."),
                server.console.messages().toString());
    }

    @Test
    void importedPunishmentsAreEnforced() {
        server.command(server.console, "judicator import judicator confirm");

        assertTrue(server.join(new FakePlayer("Anyone", "10.9.9.9")).isPresent(), "the IP ban holds");
        final FakePlayer steveOnline = server.online(new FakePlayer("Steve", "10.0.0.1"));
        assertEquals(TestServer.Outcome.BLOCKED_MUTED, server.chat(steveOnline, "hello"), "the temporary mute holds");
    }

    @Test
    void ipHistoryIsMergedWithWhatTheNewServerAlreadyKnows() {
        server.online(new FakePlayer("NewAccount", "10.0.0.1"));

        server.command(server.console, "judicator import judicator confirm");

        final AccessAddress merged = server.addresses.findByIp("10.0.0.1").orElseThrow();
        assertEquals(List.of("newaccount", "steve", "stevealt"), merged.getAccounts().stream().sorted().toList());
        assertTrue(server.addresses.findByIp("10.0.0.7").isPresent());

        server.command(server.console, "alts Steve");
        assertTrue(server.console.received("newaccount") && server.console.received("stevealt"), server.console.messages().toString());
    }

    @Test
    void runningAgainImportsNothingTwice() {
        server.command(server.console, "judicator import judicator confirm");
        server.console.clearMessages();

        server.command(server.console, "judicator import judicator confirm");

        assertTrue(server.console.received("0 punishments and 0 warns to import, 5 already in Judicator."), server.console.messages().toString());
        assertTrue(server.console.received("0 new, 0 to merge with accounts already known, 2 already up to date."), server.console.messages().toString());
        assertEquals(4, server.punishments.all().size());
    }

    @Test
    void timesAreImportedInWholeSeconds() {
        final RelationalDBManager manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", "sqlite", "file", "old.db"))), oldServer, NOPLogger.NOP_LOGGER);
        try {
            new PunishmentRelationalDAO(manager, NOPLogger.NOP_LOGGER).save(Punishment.create(steve, "fraction", "Mod", "Steve",
                    null, start.plusDays(8).plusNanos(700_000_000), null, List.of(), PunishType.MUTE));
        } finally {
            manager.shutdown();
        }

        server.command(server.console, "judicator import judicator confirm");

        final Punishment imported = server.punishments.all().stream()
                .filter(p -> p.getReason().orElse("").equals("fraction")).findFirst().orElseThrow();
        assertEquals(start.plusDays(8), imported.getStartedAt(), "MySQL DATETIME would round .7 up; the duplicate key cuts it");
    }

    @Test
    void aDatabaseOfVersion1_isReadWithoutThePrefix() throws Exception {
        final Path version1 = Files.createDirectories(oldServer.resolve("v1"));
        final RelationalDBManager manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", "sqlite", "file", "v1.db", "table-prefix", ""))), version1, NOPLogger.NOP_LOGGER);
        try {
            final PunishmentRelationalDAO punishments = new PunishmentRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            punishments.initialize();
            punishments.save(Punishment.create(null, "griefing", "Mod", "Herobrine", null, start, null, List.of(), PunishType.BAN));
        } finally {
            manager.shutdown();
        }
        Files.writeString(folder.resolve("import.yml"), "judicator:\n  jdbc-url: 'jdbc:sqlite:"
                + version1.resolve("v1.db").toString().replace("\\", "/").replace("'", "''") + "'\n", StandardCharsets.UTF_8);

        server.command(server.console, "judicator import judicator confirm");

        assertEquals(List.of("Herobrine"), server.punishments.all().stream().map(Punishment::getNickname).toList());
    }

    @Test
    void staffNotesAreCopiedOnce() {
        final RelationalDBManager manager = new RelationalDBManager();
        manager.initialize(of(map("database", map("type", "sqlite", "file", "old.db"))), oldServer, NOPLogger.NOP_LOGGER);
        try {
            final NoteRelationalDAO notes = new NoteRelationalDAO(manager, NOPLogger.NOP_LOGGER);
            notes.initialize();
            notes.save(new Note(null, steve, "Steve", "Mod", "talked to him about the x-ray", start.plusDays(7)));
        } finally {
            manager.shutdown();
        }

        server.command(server.console, "judicator import judicator confirm");
        server.command(server.console, "judicator import judicator confirm");

        assertEquals(1, server.notes.all().size());
        final Note note = server.notes.all().get(0);
        assertEquals("talked to him about the x-ray", note.getContent());
        assertEquals("Mod", note.getAuthor());
        assertEquals(start.plusDays(7), note.getCreatedAt());
        assertTrue(server.console.received("Imported 4 punishments, 1 warns and 1 staff notes from judicator"), server.console.messages().toString());
    }

    @Test
    void theSourceIsOfferedInTabCompletion() {
        assertTrue(server.suggest(server.console, "judicator import ju").contains("judicator"));
    }
}
