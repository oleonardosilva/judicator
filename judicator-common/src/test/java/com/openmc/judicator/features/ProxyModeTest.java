package com.openmc.judicator.features;

import com.openmc.judicator.notes.Note;
import com.openmc.judicator.platform.PlatformType;
import com.openmc.judicator.punish.AccessAddress;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Velocity running Judicator in front of a Spigot/Paper backend in {@code proxy-mode}, both with sync on. A player
 * online in the network is online on both, so the same {@link FakePlayer} is added to both servers: anything shown
 * twice (kick, alert, notification) would be seen.
 */
class ProxyModeTest {


    @TempDir
    Path folder;

    private final List<TestServer> servers = new ArrayList<>();
    private TestServer proxy;
    private TestServer backend;

    @BeforeEach
    void start() {
        proxy = track(TestServer.in(folder.resolve("proxy")).set("sync.enabled", true).start());
        backend = track(TestServer.in(folder.resolve("backend")).platform(PlatformType.BUKKIT)
                .set("sync.enabled", true).set("proxy-mode", true)
                .sharingDatabaseWith(proxy).start());
    }

    private TestServer track(TestServer server) {
        servers.add(server);
        return server;
    }

    @AfterEach
    void stop() {
        servers.forEach(TestServer::close);
    }

    private FakePlayer inNetwork(FakePlayer player) {
        proxy.online(player);
        backend.online(player);
        return player;
    }

    @Test
    void banOnTheProxy_kicksOnce_andTheBackendAddsNothing() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = inNetwork(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        proxy.command(proxy.console, "ban Steve hacking");
        backend.pollSync();

        assertEquals(1, steve.kicks());
        assertEquals(1, staff.messages().stream().filter(m -> m.contains("applied BAN to Steve")).count());
        assertEquals(1, staff.messages().stream().filter(m -> m.contains("User Steve was banned")).count());
    }

    @Test
    void banFromTheBackendConsole_isAppliedOnceByTheProxy() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = inNetwork(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        backend.command(backend.console, "ban Steve hacking");
        assertFalse(steve.wasKicked(), "the backend leaves the kick to the proxy");
        assertTrue(staff.messages().isEmpty(), staff.messages().toString());

        proxy.pollSync();

        assertEquals(1, steve.kicks());
        assertEquals(1, staff.messages().stream().filter(m -> m.contains("applied BAN to Steve")).count());
    }

    @Test
    void muteOnTheProxy_alertsOnce_andTheBackendBlocksTheChat() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));

        proxy.command(proxy.console, "mute Steve spam");
        backend.pollSync();

        assertEquals(Outcome.BLOCKED_MUTED, backend.chat(steve, "hello"));
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("You have been muted")).count());
    }

    @Test
    void muteFromTheBackendConsole_isNotifiedOnceByTheProxy() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = inNetwork(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        backend.command(backend.console, "mute Steve spam");
        assertEquals(Outcome.BLOCKED_MUTED, backend.chat(steve, "hello"));
        proxy.pollSync();

        assertEquals(1, staff.messages().stream().filter(m -> m.contains("applied MUTE to Steve")).count());
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("You have been muted")).count());
    }

    @Test
    void unmuteOnTheProxy_letsThePlayerTalkOnTheBackend_andTellsThemOnce() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));
        proxy.command(proxy.console, "mute Steve spam");
        backend.pollSync();

        proxy.command(proxy.console, "unmute Steve appeal");
        backend.pollSync();

        assertEquals(Outcome.ALLOWED, backend.chat(steve, "hello"));
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("Your punishment has been revoked!")).count());
    }

    @Test
    void unmuteFromTheBackendConsole_isAnnouncedToThePlayerByTheProxy() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));
        proxy.command(proxy.console, "mute Steve spam");
        backend.pollSync();

        backend.command(backend.console, "unmute Steve appeal");
        assertEquals(Outcome.ALLOWED, backend.chat(steve, "hello"));
        assertFalse(steve.received("Your punishment has been revoked!"));
        proxy.pollSync();

        assertEquals(Outcome.ALLOWED, proxy.chat(steve, "hello"));
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("Your punishment has been revoked!")).count());
    }

    @Test
    void switchingServers_doesNotPauseAnOnlineOnlyMute() {
        final FakePlayer steve = inNetwork(new FakePlayer("Steve", "10.0.0.1"));
        proxy.command(proxy.console, "tempmute Steve 1:h -o spam");
        backend.pollSync();

        backend.quit(steve);

        final Punishment mute = proxy.punishments.findAllByUsername("Steve").get(0);
        assertFalse(mute.isPaused());
        assertTrue(mute.getFinishAt().isPresent());
    }

    @Test
    void theBackendStillRefusesABannedPlayer() {
        proxy.command(proxy.console, "ban Steve hacking");

        assertTrue(backend.join(new FakePlayer("Steve", "10.0.0.1")).isPresent());
    }

    @Test
    void theBackendLoadsTheMuteOfAPlayerWhoJoinsIt() {
        proxy.command(proxy.console, "mute Steve spam");
        final FakePlayer steve = backend.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals(Outcome.BLOCKED_MUTED, backend.chat(steve, "hello"));
    }

    @Test
    void theBackendKeepsNoAddressAndSendsNoAltOrNoteWarnings() {
        final FakePlayer staff = backend.online(new FakePlayer("Helper", "10.0.0.3", "judicator.alts.notify", "judicator.notes.notify"));
        proxy.command(proxy.console, "ban Banned hacking");
        proxy.addresses.save(AccessAddress.create("10.0.0.1", "Banned"));
        proxy.command(proxy.console, "note Steve watch the chat");

        backend.online(new FakePlayer("Steve", "10.0.0.1"));

        assertTrue(proxy.addresses.findAllByUsername("steve").isEmpty());
        assertTrue(staff.messages().isEmpty(), staff.messages().toString());
    }

    @Test
    void theBackendLeavesRetentionToTheProxy() {
        proxy.notes.save(new Note(null, null, "Steve", "Mod", "old", LocalDateTime.now().minusDays(30)));

        track(TestServer.in(folder.resolve("backend2")).platform(PlatformType.BUKKIT)
                .set("sync.enabled", true).set("proxy-mode", true).set("retention.notes", 1)
                .sharingDatabaseWith(proxy).start());

        assertEquals(1, proxy.notes.countByUsername("Steve"));
    }

    @Test
    void proxyModeIsIgnoredOnVelocity() {
        final TestServer velocity = track(TestServer.in(folder.resolve("velocity")).set("proxy-mode", true).start());

        assertFalse(velocity.judicator.isProxyMode());
        assertTrue(backend.judicator.isProxyMode());
    }
}
