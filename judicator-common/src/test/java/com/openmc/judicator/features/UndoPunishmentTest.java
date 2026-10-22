package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
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
 * /unban, /unmute and /revoke: what is revoked, who can join or talk again right away, who is told.
 */
class UndoPunishmentTest {

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
    void unban_revokesEveryActiveBanAndLetsThePlayerJoin() {
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));
        server.command(server.console, "ban Steve hacking");
        final LocalDateTime now = LocalDateTime.now();
        server.punishments.save(Punishment.create(null, "older", "Mod", "Steve", null, now.minusDays(1), now.plusDays(5),
                List.of(), PunishType.TEMPBAN));

        server.command(server.console, "unban Steve appeal accepted");

        assertTrue(server.console.received("Revoked 2 active ban(s) of Steve."), server.console.messages().toString());
        assertTrue(server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.values()).isEmpty());
        server.punishments.findAllByUsername("Steve").forEach(p -> {
            assertTrue(p.isRevoked());
            assertEquals("appeal accepted", p.getRevokedReason().orElseThrow());
        });
        assertTrue(staff.received("[Staff] Console revoked 2 BAN of Steve"), staff.messages().toString());
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty());
    }

    @Test
    void longRevokeReason_isShortenedInsteadOfFailing() {
        server.command(server.console, "ban Steve hacking");
        final String reason = "x".repeat(150);

        server.command(server.console, "unban Steve " + reason);

        assertTrue(server.console.received("Revoked 1 active ban(s) of Steve."), server.console.messages().toString());
        assertEquals(100, server.punishments.all().get(0).getRevokedReason().orElseThrow().length());
    }

    @Test
    void unban_ofAPlayerWhoIsNotBanned_saysSo() {
        server.command(server.console, "unban Steve");
        assertTrue(server.console.received("This player has no active ban."));
    }

    @Test
    void unban_doesNotTouchMutes() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");
        server.command(server.console, "unban Steve");
        assertTrue(server.console.received("This player has no active ban."));
        assertEquals(1, server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.MUTE).size());
    }

    @Test
    void unmute_letsTheOnlinePlayerTalkImmediately() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(steve, "x"));

        server.command(server.console, "unmute Steve");

        assertEquals(Outcome.ALLOWED, server.chat(steve, "thanks"));
        assertTrue(steve.received("Your punishment has been revoked!"));
        assertTrue(server.console.received("Revoked 1 active mute(s) of Steve."));
    }

    @Test
    void unmuteOfAnIpMute_freesEveryAccountOnTheAddress() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.command(server.console, "muteip Steve spam");

        server.command(server.console, "unmute Steve");

        assertEquals(Outcome.ALLOWED, server.chat(steve, "a"));
        assertEquals(Outcome.ALLOWED, server.chat(alt, "b"));
        assertEquals(Outcome.ALLOWED, server.chat(server.online(new FakePlayer("Newcomer", "10.0.0.1")), "c"));
    }

    @Test
    void unmute_ofAPlayerWhoIsNotMuted_saysSo() {
        server.command(server.console, "unmute Steve");
        assertTrue(server.console.received("This player has no active mute."));
    }

    @Test
    void revoke_rejectsBadIdsAndRevokesOnlyOnce() {
        server.command(server.console, "revoke abc");
        assertTrue(server.console.received("Incorrect command. Use: /revoke (id)."));
        server.command(server.console, "revoke 999");
        assertTrue(server.console.received("No punishment was found with the provided ID."));

        server.command(server.console, "ban Steve hacking");
        final long id = server.punishments.all().get(0).getId();
        server.console.clearMessages();

        server.command(server.console, "revoke #" + id + " mistake");
        assertTrue(server.console.received("The punishment with the provided ID has been successfully revoked."));
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty());

        server.command(server.console, "revoke " + id);
        assertTrue(server.console.received("This punishment has already been revoked."));
    }

    @Test
    void revokeOfAMute_letsTheOnlinePlayerTalk() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");
        final long id = server.punishments.all().get(0).getId();

        server.command(server.console, "revoke " + id);

        assertEquals(Outcome.ALLOWED, server.chat(steve, "free"));
        assertTrue(steve.received("Your punishment has been revoked!"));
    }

    @Test
    void revokeOfAnIpMute_freesTheOtherAccountsOnTheAddressToo() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.command(server.console, "muteip Steve spam");
        final long id = server.punishments.all().get(0).getId();

        server.command(server.console, "revoke " + id);

        assertEquals(Outcome.ALLOWED, server.chat(alt, "free"));
    }

    @Test
    void playerWithoutPermission_cannotUnbanOrRevoke() {
        final FakePlayer player = server.online(new FakePlayer("Player", "10.0.0.9"));
        server.command(server.console, "ban Steve hacking");
        assertFalse(server.command(player, "unban Steve"));
        assertFalse(server.command(player, "revoke 1"));
        assertEquals(1, server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.BAN).size());
    }
}
