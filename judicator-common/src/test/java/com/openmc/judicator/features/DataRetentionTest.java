package com.openmc.judicator.features;

import com.openmc.judicator.notes.Note;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import com.openmc.judicator.warns.Warn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * retention.* (old records deleted at startup and every 12 hours) and /judicator clear (everything about a player).
 */
class DataRetentionTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private Punishment oldKick(String name) {
        final LocalDateTime start = LocalDateTime.now().minusDays(40);
        final Punishment kick = Punishment.create(null, "bye", "Mod", name, null, start, start, List.of(), PunishType.KICK);
        kick.setPermanent(false);
        return server.punishments.save(kick);
    }

    @Test
    void configuredRetention_deletesOldRecordsAtStartup() {
        server = TestServer.in(folder).set("retention.kicks", 30).start();
        final Punishment kick = oldKick("Steve");

        server.restart();

        assertTrue(server.punishments.findById(kick.getId()).isEmpty());
    }

    @Test
    void retentionRun_reportsWhatEachSettingDeleted() {
        server = TestServer.in(folder).set("retention.kicks", 30).set("retention.finished-punishments", 30).start();
        oldKick("Steve");
        final LocalDateTime old = LocalDateTime.now().minusDays(60);
        server.punishments.save(Punishment.create(null, "x", "Mod", "Alex", null, old, old.plusDays(1), List.of(), PunishType.TEMPBAN));
        server.notes.save(new Note(null, null, "Steve", "Mod", "kept: notes retention is 0", old));

        final Map<String, Integer> deleted = server.judicator.getDataRetentionService().run(LocalDateTime.now());

        assertEquals(Map.of("kicks", 1, "finished-punishments", 1), deleted);
        assertEquals(1, server.notes.all().size());
    }

    @Test
    void retention_runsAgainPeriodically() {
        server = TestServer.in(folder).set("retention.kicks", 30).start();
        final Punishment kick = oldKick("Steve");

        server.platform.runRepeatingTasks();

        assertTrue(server.punishments.findById(kick.getId()).isEmpty());
    }

    @Test
    void finishedPunishmentsRetention_keepsKicksWhenTheirRetentionIsOff() {
        server = TestServer.in(folder).set("retention.finished-punishments", 30).start();
        final Punishment kick = oldKick("Steve");

        server.judicator.getDataRetentionService().run(LocalDateTime.now());

        assertTrue(server.punishments.findById(kick.getId()).isPresent());
    }

    @Test
    void noRetention_byDefault() {
        server = TestServer.in(folder).start();
        oldKick("Steve");

        assertTrue(server.judicator.getDataRetentionService().run(LocalDateTime.now()).isEmpty());
        assertEquals(1, server.punishments.all().size());
    }

    @Test
    void clear_withoutConfirmOnlyTellsWhatIsStored() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:d spam");
        server.command(server.console, "note Steve watch");

        server.command(server.console, "judicator clear Steve");

        assertTrue(server.console.received("1 punishment(s) (1 in force), 0 warn(s), 1 staff note(s), 1 IP address record(s)"),
                server.console.messages().toString());
        assertTrue(server.console.received("The 1 punishment(s) in force will be lifted."));
        assertEquals(1, server.punishments.all().size());
        assertEquals(1, server.notes.all().size());
    }

    @Test
    void clear_deletesEverythingAboutThePlayerAndNothingElse() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1", "judicator.level.5"));
        server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.command(server.console, "tempmute Steve 1:d spam");
        server.command(server.console, "warn Steve caps");
        server.command(server.console, "note Steve watch");
        server.command(server.console, "ban Alex xray");
        server.quit(steve);
        final FakePlayer back = server.online(new FakePlayer("Steve", "10.0.0.2"));

        server.command(server.console, "judicator clear Steve confirm");

        assertTrue(server.punishments.findAllByUsername("Steve").isEmpty());
        assertTrue(server.warns.findAllByUsername("Steve").isEmpty());
        assertTrue(server.notes.all().isEmpty());
        assertTrue(server.levels.findLevel("Steve").isEmpty());
        final AccessAddress shared = server.addresses.findByIp("10.0.0.1").orElseThrow();
        assertEquals(List.of("stevealt"), shared.getAccounts());
        assertEquals(List.of("alex"), server.addresses.findByIp("10.0.0.2").orElseThrow().getAccounts());
        assertEquals(1, server.punishments.findAllByUsername("Alex").size());
        assertEquals(Outcome.ALLOWED, server.chat(back, "hi"), "the mute in force was lifted");
        assertTrue(server.console.received("Deleted the data of Steve"));
    }

    @Test
    void clear_liftsAnIpMuteForTheOtherAccountsAndDropsAddressesLeftEmpty() {
        server = TestServer.in(folder).start();
        final FakePlayer bob = server.online(new FakePlayer("Bob", "10.0.0.1"));
        server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.online(new FakePlayer("Lonely", "10.0.0.3"));
        server.command(server.console, "muteip SteveAlt spam"); // recorded as SteveAlt, reaches every account of 10.0.0.1
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(bob, "x"));

        server.command(server.console, "judicator clear SteveAlt confirm");

        assertEquals(Outcome.ALLOWED, server.chat(bob, "free"), "the deleted IP mute no longer holds Bob");
        assertEquals(List.of("bob"), server.addresses.findByIp("10.0.0.1").orElseThrow().getAccounts());
        server.command(server.console, "judicator clear Lonely confirm");
        assertTrue(server.addresses.findByIp("10.0.0.3").isEmpty(), "no account left: the row goes");
    }

    @Test
    void clear_alsoFindsRecordsOfAnOlderNameOfTheSameAccount() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.warns.save(Warn.create(steve.getUniqueId(), "old name", "Mod", "Steve_Old", LocalDateTime.now().minusDays(3), null));

        server.command(server.console, "judicator clear Steve confirm");

        assertTrue(server.warns.all().isEmpty());
    }

    @Test
    void clear_onlyFromTheConsole() {
        server = TestServer.in(folder).start();
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.9", "judicator.admin"));
        server.command(server.console, "note Steve watch");

        server.command(admin, "judicator clear Steve confirm");

        assertTrue(admin.received("Run /judicator clear from the server console."));
        assertEquals(1, server.notes.all().size());
        assertFalse(server.console.received("Deleted"));
    }
}
