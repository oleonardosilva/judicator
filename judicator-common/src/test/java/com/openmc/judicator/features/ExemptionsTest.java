package com.openmc.judicator.features;

import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * immune.yml {@code players:}: exemptions by UUID or name, per punishment type.
 */
class ExemptionsTest {

    private static final String IMMUNE = "This player cannot be punished.";

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private static UUID uuidOf(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private int active(String name, PunishType... types) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, types).size();
    }

    @Test
    void theUsersListOfVersion1_stillExemptsFromEverything() {
        // 1.0.0 shipped immune.yml with 'users:'; an upgrade must not make those players punishable.
        server = TestServer.in(folder).immuneFile("users:\n  - \"Notch\"\n").start();
        server.online(new FakePlayer("Notch", "10.0.0.1"));

        server.command(server.console, "ban Notch hacking");
        server.command(server.console, "warn Notch spam");

        assertTrue(server.console.received(IMMUNE));
        assertEquals(0, active("Notch", PunishType.BAN));
        assertTrue(server.warns.findAllByUsername("Notch").isEmpty());
    }

    @Test
    void exemptFromOneType_canStillGetTheOthers() {
        server = TestServer.in(folder).immunePlayers("  Steve: [mute]\n").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "mute Steve spam");
        assertTrue(server.console.received(IMMUNE));
        assertEquals(0, active("Steve", PunishType.MUTE));

        server.command(server.console, "ban Steve xray");
        assertEquals(1, active("Steve", PunishType.BAN));
    }

    @Test
    void exemptionByUuid_followsTheAccountAfterANameChange() {
        server = TestServer.in(folder).immunePlayers("  \"" + uuidOf("Steve") + "\": [all]\n").start();
        final FakePlayer renamed = server.online(new FakePlayer("Steve_", uuidOf("Steve"), "10.0.0.1"));

        server.command(server.console, "ban Steve_ xray");
        assertTrue(server.console.received(IMMUNE));

        server.quit(renamed);
        server.console.clearMessages();
        server.command(server.console, "tempmute Steve_ 1:h spam");
        assertTrue(server.console.received(IMMUNE), "offline: the UUID remembered at join still matches");
        assertEquals(0, server.punishments.all().size());
    }

    @Test
    void offlineMode_aUuidExemptionCoversAPlayerNotSeenSinceTheRestart() {
        server = TestServer.in(folder).offlineMode().immunePlayers("  \"" + uuidOf("Builder") + "\": [ban]\n").start();

        server.command(server.console, "ban Builder grief");

        assertTrue(server.console.received(IMMUNE), "the offline UUID of the name, not a Mojang lookup");
    }

    @Test
    void offlineMode_aUuidExemptionDoesNotDependOnTheCaseTyped() {
        server = TestServer.in(folder).offlineMode().immunePlayers("  \"" + uuidOf("Notch") + "\": [ban]\n").start();
        server.quit(server.online(new FakePlayer("Notch", "10.0.0.1")));

        server.command(server.console, "ban notch grief");

        assertTrue(server.console.received(IMMUNE), server.console.messages().toString());
    }

    @Test
    void uuidWithoutDashes_isAccepted() {
        server = TestServer.in(folder).immunePlayers("  \"" + uuidOf("Steve").toString().replace("-", "") + "\": [ban]\n").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve xray");

        assertTrue(server.console.received(IMMUNE));
    }

    @Test
    void kickAndWarnExemptions() {
        server = TestServer.in(folder).immunePlayers("  Steve: [kick, warn]\n").start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "kick Steve go");
        server.command(server.console, "warn Steve spam");

        assertTrue(!steve.wasKicked());
        assertEquals(0, server.warns.findAllByUsername("Steve").size());
        server.command(server.console, "mute Steve spam");
        assertEquals(1, active("Steve", PunishType.MUTE));
    }

    @Test
    void punishWithAReason_checksTheTypeOfTheStep() {
        server = TestServer.in(folder).immunePlayers("  Steve: [mute]\n").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "punish Steve Player Offense"); // first step: TEMPMUTE
        assertTrue(server.console.received(IMMUNE));
        server.command(server.console, "punish Steve Cheating"); // TEMPBAN
        assertEquals(1, active("Steve", PunishType.TEMPBAN));
    }

    @Test
    void ipPunishment_refusedWhenAnAccountOnTheAddressIsExemptFromIt() {
        server = TestServer.in(folder).immunePlayers("  Alex: [ban]\n").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.1"));

        server.command(server.console, "banip Steve evading");
        assertTrue(server.console.received(IMMUNE));

        server.command(server.console, "muteip Steve spam");
        assertEquals(1, active("Steve", PunishType.MUTE));
    }

    @Test
    void unknownTypes_areIgnoredAndTheRestApplies() {
        server = TestServer.in(folder).immunePlayers("  Steve: [mute, everything]\n").start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "mute Steve spam");
        assertTrue(server.console.received(IMMUNE));
        server.command(server.console, "ban Steve xray");
        assertEquals(1, active("Steve", PunishType.BAN));
    }
}
