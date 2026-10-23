package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #key in the reason of /ban, /mute, /kick, /warn and their variants is replaced by the text of reasons.key.
 */
class ReasonShortcutTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private String onlyReason() {
        final List<Punishment> all = server.punishments.all();
        assertEquals(1, all.size(), all.toString());
        return all.get(0).getReason().orElseThrow();
    }

    @Test
    void theShortcutBecomesTheConfiguredText() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve #reason2 x-ray");

        assertEquals("Cheating x-ray", onlyReason());
    }

    @Test
    void worksAnywhereInTheReasonAndIgnoresCase() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "tempmute Steve 1:h repeated #REASON1");

        assertEquals("repeated Player Offense", onlyReason());
        assertEquals(PunishType.TEMPMUTE, server.punishments.all().get(0).getType(), "type and duration stay the command's");
    }

    @Test
    void anUnknownShortcutStaysAsTyped() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve #1 offender #");

        assertEquals("#1 offender #", onlyReason());
    }

    @Test
    void manyShortcutsAreCutToTheReasonColumn() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve " + "#reason3 ".repeat(15));

        assertEquals(255, onlyReason().length());
    }

    @Test
    void worksInWarnsAndKicks() {
        server = TestServer.in(folder).set("log-kicks", true).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "warn Steve #reason3");
        server.command(server.console, "kick Steve #reason2");

        assertEquals("Serious Dissemination", server.warns.findAllByUsername("Steve").get(0).getReason());
        assertTrue(steve.kickMessage().contains("Reason: Cheating"), steve.kickMessage());
        assertEquals("Cheating", onlyReason());
    }

    @Test
    void withReasonPermissions_aShortcutNeedsItsPermission() {
        server = TestServer.in(folder).set("require-reason-permission", true).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "punish.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "ban Steve #reason2");

        assertTrue(helper.received("You do not have permission to use this reason."));
        assertTrue(server.judicator.getPunishCache().getContext("Helper").isEmpty());
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void tabCompletionSuggestsShortcutsTheSenderCanUse() {
        server = TestServer.in(folder).set("require-reason-permission", true).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.tempban", "punish.helper"));

        assertEquals(List.of("#reason1"), server.suggest(helper, "ban Steve #"));
        assertEquals(List.of("#reason1"), server.suggest(helper, "tempban Steve 1:d some #rea"));
        assertEquals(List.of("#reason1", "#reason2", "#reason3"), server.suggest(server.console, "kick Steve #"));
        assertEquals(List.of(), server.suggest(server.console, "ban Steve hacking"));
    }
}
