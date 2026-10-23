package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishStatus;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void withLogKicks_theKickGoesToTheHistoryAsAFinishedEntry() {
        server.close();
        server = TestServer.in(folder.resolve("logged")).config("log-kicks: false", "log-kicks: true").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.kick", "judicator.history"));

        server.command(mod, "kick Steve calm down");

        final List<Punishment> all = server.punishments.all();
        assertEquals(1, all.size());
        final Punishment kick = all.get(0);
        assertEquals(PunishType.KICK, kick.getType());
        assertEquals("Mod", kick.getPunisher());
        assertEquals("calm down", kick.getReason().orElseThrow());
        assertEquals(PunishStatus.FINISHED, kick.getStatus());
        assertEquals(kick.getStartedAt(), kick.getFinishAt().orElseThrow(), "stored already over, so no query sees it as active");
        assertFalse(kick.isPermanent());
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty(), "a logged kick does not block the next login");

        server.command(mod, "phistory Steve");
        assertTrue(mod.received("#" + kick.getId() + " - KICK"), mod.messages().toString());
    }

    @Test
    void loggedKicksDoNotCountForProgressiveReasons() {
        server.close();
        server = TestServer.in(folder.resolve("logged")).config("log-kicks: false", "log-kicks: true").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "kick Steve Player Offense");
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "punish Steve Player Offense");

        final Punishment mute = server.punishments.all().stream().filter(p -> p.getType() != PunishType.KICK).findFirst().orElseThrow();
        assertEquals(PunishType.TEMPMUTE, mute.getType());
        assertTrue(mute.getFinishAt().orElseThrow().isBefore(mute.getStartedAt().plusDays(8)), "first step (7 days), not the second (30 days)");
    }
}
