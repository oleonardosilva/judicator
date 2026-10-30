package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /extend and /changereason: fixing a punishment in force without revoking it.
 */
class EditPunishmentTest {

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

    private Punishment byConsole(String line, String name) {
        server.command(server.console, line);
        return only(name);
    }

    @Test
    void extend_addsTimeToTheEnd() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Punishment ban = byConsole("tempban Steve 1:d xray", "Steve");

        server.command(server.console, "extend " + ban.getId() + " 2:d");

        final Punishment extended = only("Steve");
        assertEquals(ban.getFinishAt().get().plusDays(2), extended.getFinishAt().get());
        assertEquals(PunishType.TEMPBAN, extended.getType());
        assertTrue(server.console.received("Punishment #" + ban.getId() + " of Steve now ends"));
    }

    @Test
    void extend_reachesTheCachedMuteRightAway() {
        server = TestServer.in(folder).start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Punishment mute = byConsole("tempmute Steve 1:h spam", "Steve");

        server.command(server.console, "extend " + mute.getId() + " 1:d");
        steve.clearMessages();
        server.chat(steve, "hi");

        final String end = server.judicator.getPunishCache().getPunishment(steve.getUniqueId().toString()).orElseThrow().getFinishAt().orElseThrow().toString();
        assertEquals(only("Steve").getFinishAt().orElseThrow().toString(), end);
    }

    @Test
    void extend_refusesPermanentFinishedRevokedAndKicks() {
        server = TestServer.in(folder).set("log-kicks", true).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.2"));
        final Punishment ban = byConsole("ban Steve xray", "Steve");
        server.command(server.console, "extend " + ban.getId() + " 1:d");
        assertTrue(server.console.received("Only a temporary punishment can be extended."));

        server.command(server.console, "kick Alex bye");
        final Punishment kick = only("Alex");
        server.command(server.console, "extend " + kick.getId() + " 1:d");
        assertTrue(server.console.received("Only a punishment in force can be changed."));

        server.command(server.console, "revoke " + ban.getId());
        server.console.clearMessages();
        server.command(server.console, "changereason " + ban.getId() + " other");
        assertTrue(server.console.received("Only a punishment in force can be changed."));
        assertEquals("xray", only("Steve").getReason().orElseThrow());
    }

    @Test
    void extend_cannotGoBeyondTheEditorsTimeLimit() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2",
                "judicator.tempban", "judicator.extend", "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(helper, "tempban Steve 12:h xray");
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(helper, "confirm"));
        final Punishment ban = only("Steve");

        server.command(helper, "extend " + ban.getId() + " 1:d");

        assertTrue(helper.received("Your rank can apply a ban for at most 1 day."), helper.messages().toString());
        assertEquals(ban.getFinishAt(), only("Steve").getFinishAt());
    }

    @Test
    void changeReason_replacesItAndExpandsShortcuts() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.notify"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Punishment ban = byConsole("ban Steve typo", "Steve");

        server.command(server.console, "changereason " + ban.getId() + " #reason2 x-ray");

        assertEquals("Cheating x-ray", only("Steve").getReason().orElseThrow());
        assertTrue(mod.received("[Staff] Console changed #" + ban.getId() + " of Steve: reason is now \"Cheating x-ray\""), mod.messages().toString());
    }

    @Test
    void onlyTheAuthorOrSomeoneAboveCanEdit() {
        server = TestServer.in(folder).start();
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.ban", "judicator.level.50"));
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.changereason", "judicator.level.10"));
        final FakePlayer senior = server.online(new FakePlayer("Senior", "10.0.0.4", "judicator.changereason", "judicator.level.60"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(admin, "ban Steve xray");
        server.chat(admin, "confirm");
        final Punishment ban = only("Steve");

        server.command(helper, "changereason " + ban.getId() + " nothing");
        assertTrue(helper.received("Only the author of this punishment, or a staff member above them, can change it."));
        assertEquals("xray", only("Steve").getReason().orElseThrow());

        server.command(senior, "changereason " + ban.getId() + " cheating");
        assertEquals("cheating", only("Steve").getReason().orElseThrow());
    }

    @Test
    void cannotEditThePunishmentOfAStaffMemberAboveTheEditor() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.extend", "judicator.level.10"));
        server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.level.50"));
        final LocalDateTime now = LocalDateTime.now();
        server.punishments.save(Punishment.create(null, "test", "Mod", "Admin", null, now,
                now.plusHours(1), List.of(), PunishType.TEMPMUTE));
        final Punishment mute = only("Admin");

        server.command(helper, "extend " + mute.getId() + " 1:d");

        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."));
        assertEquals(mute.getFinishAt(), only("Admin").getFinishAt());
    }

    @Test
    void whatTheConsoleApplied_isOnlyEditedByTheConsoleOrAnAdmin() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.extend", "judicator.level.10"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.admin"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Punishment ban = byConsole("tempban Steve 1:d warn tree", "Steve");

        server.command(mod, "extend " + ban.getId() + " 30:d");
        assertTrue(mod.received("Only the author of this punishment, or a staff member above them, can change it."));
        assertEquals(ban.getFinishAt(), only("Steve").getFinishAt());

        server.command(admin, "extend " + ban.getId() + " 1:d");
        assertEquals(ban.getFinishAt().get().plusDays(1), only("Steve").getFinishAt().get());
    }

    @Test
    void invalidInput_showsUsageAndPermissionIsRequired() {
        server = TestServer.in(folder).start();
        final FakePlayer nobody = server.online(new FakePlayer("Nobody", "10.0.0.9"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final Punishment ban = byConsole("tempban Steve 1:d xray", "Steve");

        server.command(server.console, "extend " + ban.getId() + " soon");
        server.command(server.console, "extend abc 1:d");
        server.command(server.console, "changereason " + ban.getId());
        assertEquals(3, server.console.messages().stream().filter(m -> m.startsWith("Incorrect command.")).count());

        assertFalse(server.command(nobody, "extend " + ban.getId() + " 1:d"));
        assertFalse(server.command(nobody, "changereason " + ban.getId() + " x"));
        assertTrue(Duration.between(LocalDateTime.now(), only("Steve").getFinishAt().orElseThrow()).toHours() < 25);
    }
}
