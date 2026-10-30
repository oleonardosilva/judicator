package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import com.elless.judicator.warns.Warn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /staffrollback: undoing what a staff member applied in a period.
 */
class StaffRollbackTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private FakePlayer rogue() {
        return server.online(new FakePlayer("Rogue", "10.0.0.66", "judicator.ban", "judicator.mute", "judicator.warn"));
    }

    private void applyAs(FakePlayer staff, String line) {
        server.command(staff, line);
        if (!line.startsWith("warn")) assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(staff, "confirm"));
    }

    private int active(String name, PunishType... types) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, types).size();
    }

    @Test
    void withoutConfirm_onlyShowsWhatWouldBeUndone() {
        server = TestServer.in(folder).set("cooldowns.ban", 0).set("cooldowns.mute", 0).set("cooldowns.warn", 0).start();
        final FakePlayer rogue = rogue();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.2"));
        applyAs(rogue, "ban Steve nothing");
        applyAs(rogue, "mute Alex nothing");
        applyAs(rogue, "warn Alex nothing");

        server.command(server.console, "staffrollback Rogue 1:d");

        assertTrue(server.console.received("Rogue applied what is still in force: 1 ban(s), 1 mute(s) and 1 active warn(s)"),
                server.console.messages().toString());
        assertTrue(server.console.clicks().contains("/staffrollback Rogue 1:d confirm"));
        assertEquals(1, active("Steve", PunishType.BAN));
        assertEquals(1, server.warns.all().size());
    }

    @Test
    void confirm_revokesBansAndMutesAndDeletesActiveWarns() {
        server = TestServer.in(folder).set("cooldowns.ban", 0).set("cooldowns.mute", 0).set("cooldowns.warn", 0).start();
        final FakePlayer rogue = rogue();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.3", "judicator.notify"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        applyAs(rogue, "ban Steve nothing");
        applyAs(rogue, "mute Alex nothing");
        applyAs(rogue, "warn Alex nothing");
        server.command(server.console, "tempmute Steve 1:h fair"); // someone else's: stays

        server.command(server.console, "staffrollback Rogue 1:d confirm");

        assertEquals(0, active("Steve", PunishType.BAN));
        assertEquals(0, active("Alex", PunishType.MUTE));
        assertEquals(1, active("Steve", PunishType.TEMPMUTE));
        final Punishment revoked = server.punishments.findAllByUsername("Steve").stream()
                .filter(p -> p.getType() == PunishType.BAN).findFirst().orElseThrow();
        assertTrue(revoked.isRevoked(), "the record stays, revoked");
        assertEquals("Rollback of Rogue by Console", revoked.getRevokedReason().orElseThrow());
        assertTrue(server.warns.all().isEmpty());
        assertEquals(Outcome.ALLOWED, server.chat(alex, "I can talk"));
        assertTrue(alex.received("Your punishment has been revoked!"));
        assertTrue(mod.received("[Staff] Console rolled back Rogue (last 1 day): 2 punishment(s) revoked, 1 warn(s) deleted"),
                mod.messages().toString());
    }

    @Test
    void outsideThePeriodAndAlreadyOver_areLeftAlone() {
        server = TestServer.in(folder).start();
        final LocalDateTime now = LocalDateTime.now();
        final Punishment old = server.punishments.save(Punishment.create(null, "old", "Rogue", "Steve", null,
                now.minusDays(10), null, List.of(), PunishType.BAN));
        final Punishment over = server.punishments.save(Punishment.create(null, "over", "Rogue", "Alex", null,
                now.minusHours(5), now.minusHours(1), List.of(), PunishType.TEMPBAN));
        server.warns.save(Warn.create(UUID.randomUUID(), "expired", "Rogue", "Alex", now.minusHours(5), now.minusHours(1)));

        server.command(server.console, "staffrollback Rogue 1:d confirm");

        assertTrue(server.console.received("Rogue applied nothing that is still in force in the last 1 day."));
        assertFalse(server.punishments.findById(old.getId()).orElseThrow().isRevoked());
        assertFalse(server.punishments.findById(over.getId()).orElseThrow().isRevoked());
        assertEquals(1, server.warns.all().size());
    }

    @Test
    void hierarchy_protectsHigherStaff() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.staffrollback", "judicator.level.10"));
        server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.level.50"));
        server.punishments.save(Punishment.create(null, "x", "Admin", "Steve", null, LocalDateTime.now().minusHours(1),
                null, List.of(), PunishType.BAN));

        server.command(helper, "staffrollback Admin 1:d confirm");

        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."));
        assertEquals(1, active("Steve", PunishType.BAN));
    }

    @Test
    void whatTheConsoleApplied_isOnlyForTheConsoleAndAdmins() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.staffrollback", "judicator.level.10"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.admin"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "ban Steve anticheat");

        server.command(mod, "staffrollback Console 1:d confirm");
        assertTrue(mod.received("You cannot punish a staff member of equal or higher rank."));
        assertEquals(1, active("Steve", PunishType.BAN));

        server.command(admin, "staffrollback Console 1:d confirm");
        assertEquals(0, active("Steve", PunishType.BAN));
    }

    @Test
    void aSoftMuteIsLiftedWithoutTellingThePlayer() {
        server = TestServer.in(folder).start();
        final FakePlayer rogue = server.online(new FakePlayer("Rogue", "10.0.0.66", "judicator.softmute"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        applyAs(rogue, "softmute Alex nothing");

        server.command(server.console, "staffrollback Rogue 1:d confirm");

        assertEquals(0, active("Alex", PunishType.MUTE));
        assertTrue(alex.messages().isEmpty(), alex.messages().toString());
    }

    @Test
    void invalidInputAndPermission() {
        server = TestServer.in(folder).start();
        final FakePlayer nobody = server.online(new FakePlayer("Nobody", "10.0.0.9"));

        server.command(server.console, "staffrollback Rogue");
        server.command(server.console, "staffrollback Rogue yesterday");
        assertEquals(2, server.console.messages().stream().filter(m -> m.startsWith("Incorrect command.")).count());
        assertFalse(server.command(nobody, "staffrollback Rogue 1:d"));
    }
}
