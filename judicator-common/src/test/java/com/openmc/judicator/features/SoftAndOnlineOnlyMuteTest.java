package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import com.openmc.judicator.testing.Texts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Soft mutes (/softmute, /tempsoftmute: only the sender sees their messages) and online-only mutes (-o: the time only
 * runs while the player is online).
 */
class SoftAndOnlineOnlyMuteTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private Punishment only(String name) {
        final List<Punishment> all = server.punishments.findAllByUsername(name);
        assertEquals(1, all.size(), all.toString());
        return all.get(0);
    }

    // --- soft mute ---

    @Test
    void softMute_theTargetNoticesNothingAndOnlyTheyReceiveTheirMessages() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.2"));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.3", "judicator.notify"));

        server.command(server.console, "softmute Steve spam bot");

        assertTrue(steve.messages().isEmpty(), "no mute alert: " + steve.messages());
        assertFalse(other.received("muted"), "no public announcement");
        assertTrue(mod.received("[Staff] Console applied SOFTMUTE to Steve: spam bot"), mod.messages().toString());
        assertEquals(Outcome.SOFT_MUTED, server.chat(steve, "buy cheap coins"));
        assertTrue(steve.messages().isEmpty(), "nothing tells them");
        assertEquals(Outcome.BLOCKED_MUTED, server.playerCommand(steve, "/tell Other hi"));
        assertTrue(steve.messages().isEmpty(), "locked commands are blocked silently");
        assertEquals(Outcome.ALLOWED, server.chat(other, "hello"));
    }

    @Test
    void softMute_isLiftedByUnmuteAndShownToStaff() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempsoftmute Steve 1:h spam");
        final Punishment mute = only("Steve");
        assertTrue(mute.isSoft());

        server.command(server.console, "pview " + mute.getId());
        assertTrue(server.console.received("Type: TEMPSOFTMUTE"), server.console.messages().toString());

        server.command(server.console, "unmute Steve");
        assertEquals(Outcome.ALLOWED, server.chat(steve, "back"));
        assertTrue(steve.messages().isEmpty(), "lifting it does not tell them either: " + steve.messages());

        server.command(server.console, "softmute Steve again");
        server.command(server.console, "revoke " + server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.MUTE).get(0).getId());
        assertTrue(steve.messages().isEmpty(), steve.messages().toString());
    }

    @Test
    void softMute_doesNothingToChatWhenGlobalChatIsNotBlocked() {
        server = TestServer.in(folder).config("block-global-chat: true", "block-global-chat: false").start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "softmute Steve spam");

        assertEquals(Outcome.ALLOWED, server.chat(steve, "hi"));
    }

    @Test
    void softMuteEcho_usesTheConfiguredFormatAndKeepsThePlayersTextPlain() {
        server = TestServer.in(folder).config("soft-mute-format: \"<{player}> {message}\"", "soft-mute-format: \"&7{player}: {message}\"").start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals("Steve: &cfree &lgems", Texts.plain(server.judicator.getChatHandler().softMuteEcho(steve, "&cfree &lgems")));
    }

    @Test
    void softMute_needsItsOwnPermission() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.3", "judicator.mute", "judicator.tempmute"));

        assertFalse(server.command(mod, "softmute Steve x"));
        assertFalse(server.command(mod, "tempsoftmute Steve 1:h x"));
    }

    // --- online-only mute ---

    @Test
    void onlineOnly_pausesWhenThePlayerLeavesAndRunsAgainWhenTheyReturn() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:h -o spam");
        assertTrue(only("Steve").isOnlineOnly());
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(steve, "hi"));

        server.quit(steve);
        final Punishment paused = only("Steve");
        assertTrue(paused.isPaused());
        assertTrue(paused.getFinishAt().isEmpty());
        assertTrue(paused.getPausedRemaining() > Duration.ofMinutes(59).toMillis()
                && paused.getPausedRemaining() <= Duration.ofHours(1).toMillis(), String.valueOf(paused.getPausedRemaining()));
        assertEquals(1, server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.TEMPMUTE).size(), "still in force");

        final FakePlayer back = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Punishment running = only("Steve");
        assertFalse(running.isPaused());
        final long left = Duration.between(LocalDateTime.now(), running.getFinishAt().orElseThrow()).toMillis();
        assertTrue(left > 0 && left <= paused.getPausedRemaining() + 1000, String.valueOf(left));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(back, "hi"));
        final LocalDateTime cachedEnd = server.judicator.getPunishCache().getPunishment("Steve").orElseThrow().getFinishAt()
                .orElseThrow(() -> new AssertionError("the cached mute must have its new end"));
        assertTrue(Math.abs(Duration.between(running.getFinishAt().orElseThrow(), cachedEnd).toMillis()) < 1000,
                "the cached mute ends when the stored one does: " + cachedEnd);
    }

    @Test
    void onlineOnly_appliedToAnOfflinePlayerStartsPaused() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));

        server.command(server.console, "tempmute Steve 2:h -o spam");

        final Punishment mute = only("Steve");
        assertEquals(Duration.ofHours(2).toMillis(), mute.getPausedRemaining());
        server.command(server.console, "pview " + mute.getId());
        assertTrue(server.console.received("End: 2 hours of online time left"), server.console.messages().toString());
        server.command(server.console, "mutelist");
        assertTrue(server.console.received("Steve"), "a paused mute is in force");
    }

    @Test
    void atLogin_aPausedShortMuteDoesNotHideALongerOne() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "tempmute Steve 5:m -o short");
        server.command(server.console, "tempmute Steve 30:d long");
        final long longId = server.punishments.findAllByUsername("Steve").stream()
                .filter(p -> !p.isOnlineOnly()).findFirst().orElseThrow().getId();

        server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals(longId, server.judicator.getPunishCache().getPunishment("Steve").orElseThrow().getId(),
                "a paused mute is not permanent: the 30-day one is the one that holds");
    }

    @Test
    void atLogin_aNormalMuteWinsOverALongerSoftOne() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "tempsoftmute Steve 30:d bot");
        server.command(server.console, "tempmute Steve 1:h flood");

        final FakePlayer back = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(back, "hi"));
    }

    @Test
    void onlineOnly_onlyAnAuthenticatedLoginResumesIt() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "tempmute Steve 1:h -o spam");

        // someone types Steve's name at the login screen but never authenticates
        server.judicator.getConnectionHandler().checkPreLogin("Steve", "10.6.6.6");

        assertTrue(only("Steve").isPaused());
    }

    @Test
    void onlineOnly_aLoginRefusedAfterAuthenticationDoesNotResumeIt() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "tempmute Steve 1:h -o spam");

        // authenticated, but the server is full / whitelisted: the player never joins
        server.judicator.getConnectionHandler().checkPreLogin("Steve", "10.0.0.1");
        server.judicator.getConnectionHandler().onLoginAllowed("Steve", "10.0.0.1");

        assertTrue(only("Steve").isPaused());
    }

    @Test
    void onlineOnly_runsAgainAfterAPluginReloadWithThePlayerOnline() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:h -o spam");

        server.reloadPlugin();

        final Punishment mute = only("Steve");
        assertFalse(mute.isPaused());
        assertTrue(mute.getFinishAt().isPresent());
    }

    @Test
    void onlineOnly_aRevokedPausedMuteStaysRevoked() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "tempmute Steve 1:h -o spam");
        server.command(server.console, "unmute Steve");

        final FakePlayer back = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertTrue(only("Steve").isRevoked());
        assertEquals(Outcome.ALLOWED, server.chat(back, "free"));
    }

    @Test
    void onlineOnly_pausedWhenTheServerStopsWithThePlayerOnline() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:h -o spam");

        server.judicator.disable();

        assertTrue(only("Steve").isPaused());
    }

    @Test
    void onlineOnly_extendAddsToWhatIsLeft() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "tempmute Steve 1:h -o spam");

        server.command(server.console, "extend " + only("Steve").getId() + " 30:m");

        assertEquals(Duration.ofMinutes(90).toMillis(), only("Steve").getPausedRemaining());
        assertTrue(only("Steve").getFinishAt().isEmpty());
    }

    @Test
    void onlineOnly_refusedOnIpMutesAndFlagsGoInAnyOrder() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.3", "judicator.notify.silent", "judicator.notify"));

        server.command(server.console, "tempmuteip Steve 1:h -o spam");
        assertTrue(server.console.received("-o (time only counts online) works on account mutes only, not on IP mutes."));
        assertTrue(server.punishments.all().isEmpty());

        server.command(server.console, "tempmute Steve -o -s 1:h spam");
        final Punishment mute = only("Steve");
        assertTrue(mute.isOnlineOnly());
        assertEquals("spam", mute.getReason().orElseThrow());
        assertTrue(staff.received("[silent]"), staff.messages().toString());
    }

    @Test
    void withoutTheFlag_aTempmuteKeepsRunningOffline() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:h spam");

        server.quit(steve);

        final Punishment mute = only("Steve");
        assertFalse(mute.isPaused());
        assertNull(mute.getPausedRemaining());
        assertTrue(mute.getFinishAt().isPresent());
    }
}
