package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /punish with the reasons of the default config.yml: "Player Offense" (progressive TEMPMUTE 7:d,2:h → TEMPMUTE
 * 30:d → MUTE), "Cheating" (TEMPBAN 7:months,2:weeks) and "Serious Dissemination" (BAN).
 */
class PunishReasonsTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private List<Punishment> history(String name) {
        return server.punishments.findAllByUsername(name).stream().sorted(Comparator.comparing(Punishment::getId)).toList();
    }

    @Test
    void unknownReason_isRefused() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "punish Steve Being Annoying");
        assertTrue(server.console.received("The provided reason was not found."));
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void progressiveReason_escalatesWithEachOffense() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final LocalDateTime before = LocalDateTime.now();

        server.command(server.console, "punish Steve Player Offense");
        server.command(server.console, "punish Steve Player Offense");
        server.command(server.console, "punish Steve Player Offense");
        server.command(server.console, "punish Steve Player Offense");

        final List<Punishment> history = history("Steve");
        assertEquals(4, history.size());
        assertEquals(PunishType.TEMPMUTE, history.get(0).getType());
        final LocalDateTime firstEnd = history.get(0).getFinishAt().orElseThrow();
        assertTrue(firstEnd.isAfter(before.plusDays(7).plusHours(1)) && firstEnd.isBefore(before.plusDays(7).plusHours(3)), firstEnd.toString());
        assertEquals(PunishType.TEMPMUTE, history.get(1).getType());
        assertTrue(history.get(1).getFinishAt().orElseThrow().isAfter(before.plusDays(29)));
        assertEquals(PunishType.MUTE, history.get(2).getType());
        assertEquals(PunishType.MUTE, history.get(3).getType(), "the last step repeats");
        history.forEach(p -> assertEquals("Player Offense", p.getReason().orElseThrow()));
    }

    @Test
    void revokedOffenses_doNotCount() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "punish Steve Player Offense");
        server.command(server.console, "unmute Steve wrong player");
        server.command(server.console, "punish Steve Player Offense");

        final List<Punishment> history = history("Steve");
        assertTrue(history.get(0).isRevoked());
        assertEquals(PunishType.TEMPMUTE, history.get(1).getType());
        assertFalse(history.get(1).getFinishAt().orElseThrow().isAfter(LocalDateTime.now().plusDays(8)), "first step again");
    }

    @Test
    void legacyReason_usesItsTypeAndDuration() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "punish Steve Cheating");

        final Punishment ban = history("Steve").get(0);
        assertEquals(PunishType.TEMPBAN, ban.getType());
        assertTrue(ban.getFinishAt().orElseThrow().isAfter(LocalDateTime.now().plusMonths(7)));
        assertTrue(steve.wasKicked());
    }

    @Test
    void reasonPermission_isOnlyCheckedWhenEnabled() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.punish"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "punish Steve Cheating");
        assertFalse(helper.received("You do not have permission to use this reason."), "default: not enforced");
        server.close();

        server = TestServer.in(folder.resolve("enforced"))
                .set("require-reason-permission", true)
                .start();
        final FakePlayer helper2 = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.punish"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper2, "punish Steve Cheating");
        assertTrue(helper2.received("You do not have permission to use this reason."));
        assertTrue(server.punishments.all().isEmpty());
        assertFalse(server.suggest(helper2, "punish Steve Ch").contains("Cheating"));

        helper2.grant("punish.moderator");
        assertTrue(server.suggest(helper2, "punish Steve Ch").contains("Cheating"));
        assertTrue(server.suggest(helper2, "punish Steve -s Ch").contains("Cheating"), "suggestions skip the -s flag");
    }

    @Test
    void menuForStaff_listsTheReasons() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.punish"));

        server.command(helper, "punish Steve");

        assertTrue(helper.received("Player Offense"), helper.messages().toString());
        assertTrue(helper.received("Cheating"));
        assertTrue(server.punishments.all().isEmpty());
    }
}
