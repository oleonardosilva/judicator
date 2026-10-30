package com.elless.judicator.features;

import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may punish whom: self, immune list, staff hierarchy (judicator.level.N), IP punishments that would reach a
 * protected account; and silent punishments with staff notifications.
 */
class StaffRulesTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private boolean banned(String name) {
        return !server.punishments.findAllActiveByUsernameAndTypes(name, PunishType.BAN, PunishType.TEMPBAN).isEmpty();
    }

    /**
     * Staff players confirm their punishments in chat.
     */
    private void staffPunishes(FakePlayer staff, String line) {
        assertTrue(server.command(staff, line), staff + " may use " + line);
        if (server.judicator.getPunishCache().getContext(staff.getName()).isPresent()) {
            assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(staff, "confirm"));
        }
    }

    @Test
    void nobodyCanPunishThemselves() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.command(mod, "ban mod testing");
        assertTrue(mod.received("You cannot punish yourself."));
        assertFalse(banned("Mod"));
    }

    @Test
    void immunePlayers_cannotBePunishedEvenByTheConsole() {
        server = TestServer.in(folder).immunePlayers("  Notch: [all]\n").start();
        server.online(new FakePlayer("Notch", "10.0.0.1"));
        server.command(server.console, "ban notch hacking");
        server.command(server.console, "mute Notch spam");
        assertTrue(server.console.received("This player cannot be punished."));
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void staffCanOnlyPunishLowerLevels() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.level.10"));
        final FakePlayer otherHelper = server.online(new FakePlayer("Helper2", "10.0.0.4", "judicator.ban", "judicator.level.10"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.admin", "judicator.level.50"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "ban Admin abuse");
        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."));
        assertTrue(server.judicator.getPunishCache().getContext("Helper").isEmpty(), "refused before the confirmation step");
        assertFalse(banned("Admin"));

        helper.clearMessages();
        server.command(helper, "ban Helper2 abuse");
        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."), "same level is refused too");
        assertTrue(server.judicator.getPunishCache().getContext("Helper").isEmpty());
        assertFalse(banned("Helper2"));

        staffPunishes(helper, "ban Steve hacking");
        assertTrue(banned("Steve"), "players without a level can be punished by anyone");

        staffPunishes(admin, "ban Helper abuse");
        assertTrue(banned("Helper"));
        assertFalse(otherHelper.wasKicked());
    }

    @Test
    void levelIsRememberedForOfflineStaff() {
        server = TestServer.in(folder).start();
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.level.50"));
        server.quit(admin);
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.level.10"));

        server.command(helper, "ban Admin abuse");

        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."));
        assertFalse(banned("Admin"));
    }

    @Test
    void hierarchyAlsoProtectsAgainstKickWarnAndPunish() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2",
                "judicator.kick", "judicator.warn", "judicator.punish", "judicator.level.10"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.level.50"));

        server.command(helper, "kick Admin bye");
        server.command(helper, "warn Admin rude");
        server.command(helper, "punish Admin Cheating");

        assertEquals(3, helper.messages().stream().filter(m -> m.contains("You cannot punish a staff member")).count(), helper.messages().toString());
        assertFalse(admin.wasKicked());
        assertTrue(server.warns.all().isEmpty());
        assertTrue(server.judicator.getPunishCache().getContext("Helper").isEmpty());
    }

    @Test
    void consoleIgnoresTheHierarchy() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.level.50"));
        server.command(server.console, "ban Admin compromised");
        assertTrue(banned("Admin"));
    }

    @Test
    void hierarchyCanBeTurnedOff() {
        server = TestServer.in(folder)
                .set("hierarchy.enabled", false)
                .start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.level.10"));
        server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.level.50"));

        staffPunishes(helper, "ban Admin abuse");

        assertTrue(banned("Admin"));
    }

    @Test
    void ipBanThatWouldReachHigherStaff_isRefused() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban.ip", "judicator.level.10"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.1", "judicator.level.50"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "banip Steve shared");

        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."));
        assertTrue(server.punishments.all().isEmpty());
        assertFalse(admin.wasKicked());
    }

    @Test
    void silentPunishment_needsPermissionAndOnlyReachesSilentStaff() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));
        final FakePlayer senior = server.online(new FakePlayer("Senior", "10.0.0.4", "judicator.notify", "judicator.notify.silent"));
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.5"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve -s hacking");
        assertTrue(mod.received("You do not have permission to punish silently (-s)."));
        assertFalse(banned("Steve"));

        mod.grant("judicator.silent");
        staffPunishes(mod, "ban Steve -s hacking");

        assertTrue(banned("Steve"));
        assertFalse(bystander.received("was banned"), bystander.messages().toString());
        assertFalse(staff.received("[Staff]"), staff.messages().toString());
        assertTrue(senior.received("[silent] [Staff] Mod applied BAN to Steve: hacking"), senior.messages().toString());
    }

    @Test
    void silentFlagAlsoWorksAfterTheDuration() {
        server = TestServer.in(folder).start();
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.5"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "tempmute Steve 1:h -s spam");

        assertEquals("spam", server.punishments.all().get(0).getReason().orElseThrow());
        assertFalse(bystander.received("was muted"));
    }

    @Test
    void staffNotificationsCanBeTurnedOff() {
        server = TestServer.in(folder).set("staff-notifications", false).start();
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve hacking");

        assertFalse(staff.received("[Staff]"));
    }
}
