package com.openmc.judicator.features;

import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KickTest {

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
    void kick_disconnectsAnnouncesAndDoesNotPunish() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        server.command(server.console, "kick Steve calm down");

        assertTrue(steve.wasKicked());
        assertTrue(steve.kickMessage().contains("You were kicked from the server."));
        assertTrue(steve.kickMessage().contains("Reason: calm down"));
        assertTrue(staff.received("User Steve was kicked by Console."));
        assertTrue(staff.received("[Staff] Console applied KICK to Steve: calm down"));
        assertTrue(server.punishments.all().isEmpty());
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty(), "a kick does not block the next login");
    }

    @Test
    void kickWithoutReason_usesNone() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "kick Steve");
        assertTrue(steve.kickMessage().contains("Reason: None"), steve.kickMessage());
    }

    @Test
    void kickOfAnOfflinePlayer_saysSo() {
        server.command(server.console, "kick Ghost");
        assertTrue(server.console.received("The player is not online."));
    }

    @Test
    void silentKickWithoutPermission_isRefusedAndKicksNobody() {
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.kick"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "kick Steve -s afk");

        assertTrue(mod.received("You do not have permission to punish silently (-s)."));
        assertFalse(steve.wasKicked());
    }
}
