package com.openmc.judicator.features;

import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.warns.Warn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /warn, /tempwarn, the warn tree (commands run when a player reaches N active warns), /unwarn, /whistory, /wview.
 */
class WarnTest {

    @TempDir
    Path folder;

    private TestServer server;

    @BeforeEach
    void start() {
        // Default config: at 3 warns run "tempban {player} '3:d' got three warns".
        server = TestServer.in(folder).start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void warnNeedsAReason() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve");
        assertTrue(server.console.received("Incorrect command. Use: /warn (player) (reason)."));
        assertTrue(server.warns.all().isEmpty());
    }

    @Test
    void warn_storesAlertsAnnouncesAndNotifiesStaff() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        server.command(server.console, "warn Steve toxic language");

        final Warn warn = server.warns.findAllByUsername("steve").get(0);
        assertEquals("toxic language", warn.getReason());
        assertEquals("Console", warn.getPunisher());
        assertTrue(warn.isPermanent());
        assertTrue(steve.received("You received a warning from Console."), steve.messages().toString());
        assertTrue(steve.received("Reason: toxic language"));
        assertTrue(staff.received("User Steve was warned by Console."));
        assertTrue(staff.received("[Staff] Console applied WARN to Steve: toxic language"));
    }

    @Test
    void thirdActiveWarn_runsTheConfiguredCommandOnce() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "warn Steve one");
        server.command(server.console, "warn Steve two");
        assertTrue(server.platform.consoleCommands().isEmpty());

        server.command(server.console, "warn Steve three");
        assertEquals(List.of("tempban Steve '3:d' got three warns"), server.platform.consoleCommands());

        server.command(server.console, "warn Steve four");
        assertEquals(1, server.platform.consoleCommands().size(), "only the exact count triggers the action");
    }

    @Test
    void expiredWarns_doNotCountTowardsTheTree() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final LocalDateTime start = LocalDateTime.now().minusDays(10);
        server.warns.save(Warn.create(steve.getUniqueId(), "old", "Mod", "Steve", start, start.plusDays(1)));
        server.warns.save(Warn.create(steve.getUniqueId(), "old", "Mod", "Steve", start, start.plusDays(1)));

        server.command(server.console, "warn Steve new");

        assertTrue(server.platform.consoleCommands().isEmpty());
    }

    @Test
    void warnTreeCommand_reallyTempbansThePlayerWhenRunByTheConsole() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve one");
        server.command(server.console, "warn Steve two");
        server.command(server.console, "warn Steve three");

        // What the platform would do with the recorded console command.
        server.platform.consoleCommands().forEach(command -> server.command(server.console, command));

        assertTrue(steve.wasKicked());
        assertEquals("got three warns", server.punishments.findAllActiveByUsernameAndTypes("Steve",
                com.openmc.judicator.punish.types.PunishType.TEMPBAN).get(0).getReason().orElseThrow());
    }

    @Test
    void unwarn_lowersTheCountSoTheTreeFiresAgainAtThree() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve one");
        server.command(server.console, "warn Steve two");
        server.command(server.console, "unwarn " + server.warns.all().get(0).getId());
        server.command(server.console, "warn Steve three");
        assertTrue(server.platform.consoleCommands().isEmpty(), "only two active warns");

        server.command(server.console, "warn Steve four");
        assertEquals(1, server.platform.consoleCommands().size());
    }

    @Test
    void warnOfAPlayerWhoNeverJoined_isStoredNotLost() {
        // No UUID can be found (Mojang lookups are off in tests, like an unreachable API); warns.player_uuid is
        // NOT NULL, so the warn used to be rejected by the database while staff was told it worked.
        server.command(server.console, "warn Stranger rude in discord");

        final Warn warn = server.warns.findAllByUsername("Stranger").get(0);
        assertTrue(warn.getPlayerUUID().isPresent());
        assertEquals("rude in discord", warn.getReason());
    }

    @Test
    void warnTreeCommand_neverCarriesCharactersFromThePlayerName() {
        // Offline-mode servers accept any name; the name ends up in a console command.
        server.online(new FakePlayer("Ev;il", "10.0.0.1"));
        server.command(server.console, "warn Ev;il a");
        server.command(server.console, "warn Ev;il b");
        server.command(server.console, "warn Ev;il c");

        assertEquals(List.of("tempban Evil '3:d' got three warns"), server.platform.consoleCommands());
    }

    @Test
    void tempwarn_validatesTheDurationAndExpires() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "tempwarn Steve soon spam");
        assertTrue(server.console.received("Incorrect command. Use: /tempwarn"));

        server.command(server.console, "tempwarn Steve 7:d spam");
        final Warn warn = server.warns.findAllByUsername("Steve").get(0);
        assertFalse(warn.isPermanent());
        assertTrue(warn.getFinishAt().orElseThrow().isAfter(LocalDateTime.now().plusDays(6)));
    }

    @Test
    void unwarn_deletesByIdAndTellsThePlayer() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve toxic");
        final long id = server.warns.all().get(0).getId();

        server.command(server.console, "unwarn #" + id);

        assertTrue(server.warns.all().isEmpty());
        assertTrue(server.console.received("The warn with the provided ID has been successfully deleted."));
        assertTrue(steve.received("Your warn has been deleted!"));

        server.command(server.console, "unwarn " + id);
        assertTrue(server.console.received("No warn was found with the provided ID."));
    }

    @Test
    void unwarnUsage_asksForTheId() {
        server.command(server.console, "unwarn Steve");
        assertTrue(server.console.received("Use: /unwarn (id)."), server.console.messages().toString());
    }

    @Test
    void unwarnPermission_isEnoughForUnwarn() {
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.unwarn"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve toxic");
        final long id = server.warns.all().get(0).getId();

        assertTrue(server.command(helper, "unwarn " + id));
        assertTrue(server.warns.all().isEmpty());
        assertFalse(server.command(helper, "warn Steve again"), "unwarn does not grant warn");
    }

    @Test
    void historyAndView_showTheWarns() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve toxic");
        final long id = server.warns.all().get(0).getId();

        server.command(server.console, "whistory Steve");
        assertTrue(server.console.received("Warns for player Steve"), server.console.messages().toString());
        assertTrue(server.console.received("#" + id + " - toxic"));

        server.command(server.console, "wview " + id);
        assertTrue(server.console.received("Warn ID: #" + id));
        assertTrue(server.console.received("Reason: toxic"));
    }
}
