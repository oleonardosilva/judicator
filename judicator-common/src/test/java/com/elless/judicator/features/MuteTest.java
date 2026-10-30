package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
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
 * /mute, /tempmute, /muteip and what a muted player can still do (chat, commands, after relogging).
 */
class MuteTest {

    @TempDir
    Path folder;

    private TestServer server;

    @BeforeEach
    void start() {
        server = TestServer.in(folder).start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void withoutTarget_showsUsage() {
        server.command(server.console, "mute");
        assertTrue(server.console.received("Incorrect command. Use: /mute"));
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void mute_alertsBlocksChatAndAnnounces() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.2"));
        assertEquals(Outcome.ALLOWED, server.chat(steve, "hi"));

        server.command(server.console, "mute Steve spam");

        final Punishment mute = server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.MUTE).get(0);
        assertEquals("spam", mute.getReason().orElseThrow());
        assertTrue(steve.received("You have been muted on the server."), steve.messages().toString());
        assertTrue(bystander.received("User Steve was muted by Console."));
        assertFalse(steve.wasKicked());

        steve.clearMessages();
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(steve, "hello?"));
        assertTrue(steve.received("You are currently muted on the server."));
        assertEquals(Outcome.ALLOWED, server.chat(bystander, "hello"));
    }

    @Test
    void mutedPlayer_isBlockedOnlyOnTheConfiguredCommands() {
        // Default config: commands-locked-when-silenced = /g, /l, /tell
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");

        assertEquals(Outcome.BLOCKED_MUTED, server.playerCommand(steve, "/tell Bob hi"));
        assertEquals(Outcome.BLOCKED_MUTED, server.playerCommand(steve, "/TELL Bob hi"));
        assertEquals(Outcome.BLOCKED_MUTED, server.playerCommand(steve, "g hello"), "Velocity passes commands without the slash");
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/help"));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/spawn"));
    }

    @Test
    void commandThatIsOnlyPartOfALockedCommand_isNotBlocked() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");

        // "/t" and "e" are not "/tell": a muted player must still be able to use them.
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/t"));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "e"));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/tellraw @a hi"));
    }

    @Test
    void notMutedPlayer_isNeverBlocked() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/tell Bob hi"));
    }

    @Test
    void tempmuteThatEndedWhilePlayerWasOnline_letsThemTalk() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final LocalDateTime start = LocalDateTime.now().minusMinutes(10);
        final Punishment ended = server.punishments.save(Punishment.create(steve.getUniqueId(), "spam", "Console", "Steve",
                null, start, start.plusMinutes(5), List.of(), PunishType.TEMPMUTE));
        // What MuteHandler leaves in the cache when the mute was applied while Steve was online.
        server.judicator.getPunishCache().putMutePunishment("steve", ended);

        assertEquals(Outcome.ALLOWED, server.chat(steve, "am I free?"));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/tell Bob hi"));
    }

    @Test
    void mute_survivesRelog() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:d spam");
        server.quit(steve);

        final FakePlayer again = server.online(new FakePlayer("Steve", "10.0.0.1"));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(again, "back"));
    }

    @Test
    void muteAppliedWhileThePlayerWasOffline_appliesOnTheirNextJoin() {
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "mute Steve spam");

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(server.online(new FakePlayer("Steve", "10.0.0.1")), "hi"));
    }

    @Test
    void expiredTempmute_isNotRestoredAtLogin() {
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        final LocalDateTime start = LocalDateTime.now().minusDays(3);
        server.punishments.save(Punishment.create(steve.getUniqueId(), "spam", "Console", "Steve", null,
                start, start.plusDays(1), List.of(), PunishType.TEMPMUTE));

        assertEquals(Outcome.ALLOWED, server.chat(server.online(steve), "hi"));
    }

    @Test
    void mutesSurviveAServerRestart() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Ip", "10.0.0.2"));
        server.command(server.console, "mute Steve spam");
        server.command(server.console, "muteip Ip spam");

        server.restart();

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(server.online(new FakePlayer("Steve", "10.0.0.1")), "a"));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(server.online(new FakePlayer("IpAlt", "10.0.0.2")), "b"));
        assertEquals(Outcome.ALLOWED, server.chat(server.online(new FakePlayer("Clean", "10.0.0.3")), "c"));
    }

    @Test
    void quittingForgetsTheCachedMuteOfThatName() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alt", "10.0.0.1"));
        server.command(server.console, "muteip Steve spam");
        server.quit(steve);

        assertTrue(server.judicator.getPunishCache().getPunishment(steve.getUniqueId().toString()).isEmpty(), "no leftover entry for a player who left");
    }

    @Test
    void nameMute_doesNotMuteOtherAccountsOnTheSameAddress() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer brother = server.online(new FakePlayer("Brother", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");
        assertEquals(Outcome.ALLOWED, server.chat(brother, "hi"));
    }

    @Test
    void muteip_mutesEveryAccountOnTheAddress() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.2"));

        server.command(server.console, "muteip Steve spam");

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(steve, "a"));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(alt, "b"));
        assertEquals(Outcome.ALLOWED, server.chat(other, "c"));
    }

    @Test
    void ipMute_survivesRelogOnOnlineModeServers() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.command(server.console, "muteip Steve spam");
        server.quit(alt);

        final FakePlayer altAgain = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(altAgain, "back"));
    }

    @Test
    void ipMute_appliesToANewAccountJoiningFromTheAddress() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "muteip Steve spam");

        final FakePlayer newcomer = server.online(new FakePlayer("Newcomer", "10.0.0.1"));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(newcomer, "hi"));
    }
}
