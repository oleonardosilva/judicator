package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.sync.SyncMessage;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sync.enabled}: two Spigot/Paper servers on one database, no proxy. What one applies, revokes or edits
 * reaches the players of the other at its next poll.
 */
class CrossServerSyncTest {


    @TempDir
    Path folder;

    private final List<TestServer> servers = new ArrayList<>();
    private TestServer a;
    private TestServer b;

    @BeforeEach
    void start() {
        a = server("a", null);
        b = server("b", a);
    }

    private TestServer server(String name, TestServer sharing) {
        final TestServer.Builder builder = TestServer.in(folder.resolve(name)).set("sync.enabled", true);
        if (sharing != null) builder.sharingDatabaseWith(sharing);
        final TestServer server = builder.start();
        servers.add(server);
        return server;
    }

    @AfterEach
    void stop() {
        servers.forEach(TestServer::close);
    }

    @Test
    void banOnA_kicksThePlayerOnB_announcesThereAndNotifiesItsStaff() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer bystander = b.online(new FakePlayer("Alex", "10.0.0.2"));
        final FakePlayer staffOnB = b.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        a.command(a.console, "ban Steve hacking");
        assertFalse(steve.wasKicked(), "nothing reaches B before its poll");

        b.pollSync();

        assertTrue(steve.wasKicked());
        assertTrue(steve.kickMessage().contains("You have been banned from the server."), steve.kickMessage());
        assertTrue(bystander.received("User Steve was banned by Console."), bystander.messages().toString());
        assertTrue(staffOnB.received("[Staff] Console applied BAN to Steve: hacking"), staffOnB.messages().toString());
    }

    @Test
    void ipBanOnA_kicksEveryAccountOfThatAddressOnB() {
        a.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = b.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        final FakePlayer other = b.online(new FakePlayer("Alex", "10.0.0.2"));

        a.command(a.console, "banip Steve evasion");
        b.pollSync();

        assertTrue(alt.wasKicked());
        assertFalse(other.wasKicked());
    }

    @Test
    void aServerDoesNotApplyItsOwnNoticesAgain() {
        final FakePlayer staffOnA = a.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));
        final FakePlayer steve = a.online(new FakePlayer("Steve", "10.0.0.1"));

        a.command(a.console, "ban Steve hacking");
        a.pollSync();

        assertEquals(1, steve.kicks());
        assertEquals(1, staffOnA.messages().stream().filter(m -> m.contains("applied BAN to Steve")).count());
    }

    @Test
    void muteOnA_blocksTheChatOfThePlayerOnB() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));

        a.command(a.console, "mute Steve spam");
        assertEquals(Outcome.ALLOWED, b.chat(steve, "hello"), "nothing reaches B before its poll");
        b.pollSync();

        assertEquals(Outcome.BLOCKED_MUTED, b.chat(steve, "hello"));
        assertTrue(steve.received("You have been muted on the server."), steve.messages().toString());
    }

    @Test
    void unmuteOnA_letsThePlayerTalkOnBRightAwayAndTellsThem() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        a.command(a.console, "mute Steve spam");
        b.pollSync();

        a.command(a.console, "unmute Steve appeal");
        b.pollSync();

        assertEquals(Outcome.ALLOWED, b.chat(steve, "hello"));
        assertTrue(steve.received("Your punishment has been revoked!"), steve.messages().toString());
    }

    @Test
    void revokeAndRollbackOnA_liftTheMuteOnB() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alex = b.online(new FakePlayer("Alex", "10.0.0.2"));
        a.command(a.console, "mute Steve spam");
        b.command(b.console, "mute Alex spam");
        a.pollSync();
        b.pollSync();

        final Punishment steveMute = a.punishments.findAllByUsername("Steve").get(0);
        a.command(a.console, "revoke " + steveMute.getId() + " mistake");
        a.command(a.console, "staffrollback Console 1:h confirm");
        b.pollSync();

        assertEquals(Outcome.ALLOWED, b.chat(steve, "hello"));
        assertEquals(Outcome.ALLOWED, b.chat(alex, "hello"));
    }

    @Test
    void aPlayerWithANormalAndASoftMute_isToldOnceWhenBothAreLifted() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        a.command(a.console, "softmute Steve flood");
        a.command(a.console, "mute Steve spam");
        b.pollSync();

        a.command(a.console, "unmute Steve appeal");
        b.pollSync();

        assertEquals(1, steve.messages().stream().filter(m -> m.contains("Your punishment has been revoked!")).count());
    }

    @Test
    void aPlayerWithOnlySoftMutes_isNotToldWhenTheyAreLifted() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        a.command(a.console, "softmute Steve flood");
        b.pollSync();

        a.command(a.console, "unmute Steve appeal");
        b.pollSync();

        assertEquals(Outcome.ALLOWED, b.chat(steve, "hello"));
        assertFalse(steve.received("Your punishment has been revoked!"));
    }

    @Test
    void extendOnA_changesTheEndOfTheMuteCachedOnB() {
        b.online(new FakePlayer("Steve", "10.0.0.1"));
        a.command(a.console, "tempmute Steve 1:h spam");
        b.pollSync();
        final Punishment mute = a.punishments.findAllByUsername("Steve").get(0);

        a.command(a.console, "extend " + mute.getId() + " 1:d");
        b.pollSync();

        final Punishment cached = b.judicator.getPunishCache().getPunishment("steve").orElseThrow();
        assertEquals(mute.getFinishAt().orElseThrow().plusDays(1).truncatedTo(ChronoUnit.SECONDS),
                cached.getFinishAt().orElseThrow().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void clearOnA_liftsTheMuteOnB() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        a.command(a.console, "mute Steve spam");
        b.pollSync();

        a.command(a.console, "judicator clear Steve confirm");
        b.pollSync();

        assertEquals(Outcome.ALLOWED, b.chat(steve, "hello"));
    }

    @Test
    void aPunishmentOnA_startsTheCooldownOnB() {
        b.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer mod = b.online(new FakePlayer("Mod", "10.0.0.5", "judicator.ban"));

        a.command(a.console, "tempban Steve 1:h hacking");
        b.pollSync();
        b.command(mod, "ban Steve hacking");

        assertTrue(mod.received("Steve received a ban 0s ago."), mod.messages().toString());
    }

    @Test
    void anOnlineOnlyMuteAppliedOnA_runsFromNowOnTheServerThePlayerIsOn() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));

        a.command(a.console, "tempmute Steve 1:h -o spam");
        assertTrue(a.punishments.findAllByUsername("Steve").get(0).isPaused(), "A does not see Steve online");
        b.pollSync();

        final Punishment mute = a.punishments.findAllByUsername("Steve").get(0);
        assertFalse(mute.isPaused());
        assertTrue(mute.getFinishAt().orElseThrow().isAfter(java.time.LocalDateTime.now().plusMinutes(59)));
        assertEquals(Outcome.BLOCKED_MUTED, b.chat(steve, "hello"));
        a.pollSync();
        assertFalse(a.judicator.getPunishCache().getPunishment("steve").isPresent());
    }

    @Test
    void noticeIdsStartingAgain_areReadAfterTheNextCleanup() {
        a.command(a.console, "ban Steve hacking");
        a.command(a.console, "ban Notch hacking");
        b.pollSync();
        // MySQL 5.7: the database restarts with the table empty and forgets the AUTO_INCREMENT counter
        a.sync.deleteUpTo(Long.MAX_VALUE);
        a.sync.restartIds();
        final FakePlayer alex = b.online(new FakePlayer("Alex", "10.0.0.2"));
        a.command(a.console, "ban Alex hacking");
        b.pollSync();

        b.judicator.getSyncService().cleanup();
        b.pollSync();

        assertTrue(alex.wasKicked());
        assertEquals(1, a.sync.size(), "the new notice is not deleted by the old cleanup mark");
    }

    @Test
    void aServerThatStartsLater_skipsWhatHappenedBefore() {
        a.command(a.console, "mute Steve spam");
        final TestServer c = server("c", a);
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        c.platform.add(steve);

        c.pollSync();

        assertFalse(steve.received("You have been muted on the server."), steve.messages().toString());
    }

    @Test
    void aNoticeCommittedAfterALaterOne_isStillApplied() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alex = b.online(new FakePlayer("Alex", "10.0.0.2"));
        a.command(a.console, "mute Steve spam");
        a.command(a.console, "mute Alex spam");
        // the first insert has not committed yet when B reads the second
        final SyncMessage late = a.sync.findAfter(0, 10).get(0);
        a.sync.deleteUpTo(late.id());
        b.pollSync();
        assertEquals(Outcome.BLOCKED_MUTED, b.chat(alex, "hello"));
        assertEquals(Outcome.ALLOWED, b.chat(steve, "hello"));

        a.sync.restore(late);
        b.pollSync();

        assertEquals(Outcome.BLOCKED_MUTED, b.chat(steve, "hello"));
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("You have been muted")).count());
    }

    @Test
    void aNoticeOfAnUnknownKind_isSkipped() {
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));
        a.sync.insert("a-newer-version", "SOMETHING_NEW", 1);
        a.command(a.console, "ban Steve hacking");

        b.pollSync();

        assertTrue(steve.wasKicked());
    }

    @Test
    void noticesAreDeletedOnceEveryServerHadAMinuteToReadThem() {
        a.command(a.console, "ban Steve hacking");
        b.pollSync();
        b.judicator.getSyncService().cleanup();
        assertEquals(1, a.sync.size(), "seen just now: kept until the next cleanup");

        b.judicator.getSyncService().cleanup();

        assertEquals(0, a.sync.size());
    }

    @Test
    void syncIsOffByDefault_noNoticeIsWritten() {
        final TestServer plain = TestServer.in(folder.resolve("plain")).start();
        servers.add(plain);
        final FakePlayer steve = plain.online(new FakePlayer("Steve", "10.0.0.1"));

        plain.command(plain.console, "ban Steve hacking");
        plain.pollSync();

        assertFalse(plain.judicator.getSyncService().isEnabled());
        assertEquals(0, plain.sync.size());
        assertEquals(1, steve.kicks());
        assertTrue(plain.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.BAN).size() == 1);
    }
}
