package com.openmc.judicator.features;

import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Longest punishment per staff group (time-limits + judicator.timelimit.group) and the cooldown that keeps two staff
 * members from punishing the same player for the same thing at once.
 */
class StaffLimitsTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private int bans(String name) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, PunishType.BAN, PunishType.TEMPBAN).size();
    }

    private int mutes(String name) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, PunishType.MUTE, PunishType.TEMPMUTE).size();
    }

    private void confirm(FakePlayer staff) {
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(staff, "confirm"));
    }

    @Test
    void undoingAPunishment_letsItBeAppliedAgainRightAway() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.tempban", "judicator.unban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "tempban Steve 1:h wrong duration");
        confirm(mod);

        server.command(mod, "unban Steve");
        server.command(mod, "tempban Steve 1:d xray");
        confirm(mod);

        assertEquals(1, bans("Steve"));
        assertEquals(2, server.punishments.findAllByUsername("Steve").size());
    }

    // --- time limits ---

    @Test
    void limitedStaff_canPunishWithinTheLimit() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.tempban", "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "tempban Steve 1:d griefing");
        confirm(helper);

        assertEquals(1, bans("Steve"));
    }

    @Test
    void limitedStaff_cannotGoBeyondTheLimit() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.tempban", "judicator.tempmute",
                "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "tempban Steve 2:d griefing");
        assertTrue(helper.received("Your rank can apply a ban for at most 1 day."), helper.messages().toString());
        assertTrue(server.judicator.getPunishCache().getContext("Helper").isEmpty(), "refused before the confirmation step");

        helper.clearMessages();
        server.command(helper, "tempmute Steve 8:d spam");
        assertTrue(helper.received("Your rank can apply a mute for at most 1 week."), helper.messages().toString());

        server.command(helper, "tempmute Steve 7:d spam");
        confirm(helper);
        assertEquals(0, bans("Steve"));
        assertEquals(1, mutes("Steve"));
    }

    @Test
    void limitedStaff_cannotPunishPermanently() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.ban.ip",
                "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "ban Steve griefing");
        server.command(helper, "banip Steve griefing");

        assertTrue(helper.received("Your rank can apply a ban for at most 1 day."));
        assertTrue(server.judicator.getPunishCache().getContext("Helper").isEmpty());
        assertEquals(0, bans("Steve"));
    }

    @Test
    void withSeveralGroups_theLongestLimitWins() {
        server = TestServer.in(folder)
                .set("time-limits.senior", Map.of("ban", "30:d"))
                .start();
        final FakePlayer staff = server.online(new FakePlayer("Staff", "10.0.0.2", "judicator.tempban",
                "judicator.timelimit.helper", "judicator.timelimit.senior"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(staff, "tempban Steve 7:d griefing");
        confirm(staff);

        assertEquals(1, bans("Steve"));
    }

    @Test
    void anImpossibleDurationIsIgnoredInsteadOfBreakingTheStartup() {
        server = TestServer.in(folder).set("time-limits.helper.ban", "999999999:y").start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "ban Steve griefing");
        confirm(helper);

        assertEquals(1, bans("Steve"));
    }

    @Test
    void aGroupWithoutTheCategory_doesNotLimitIt() {
        server = TestServer.in(folder)
                .remove("time-limits.helper.ban")
                .start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "ban Steve griefing");
        confirm(helper);

        assertEquals(1, bans("Steve"));
    }

    @Test
    void adminsAndTheConsole_haveNoLimit() {
        server = TestServer.in(folder).start();
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.2", "judicator.admin", "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.3"));

        server.command(admin, "ban Steve griefing");
        confirm(admin);
        server.command(server.console, "ban Alex griefing");

        assertEquals(1, bans("Steve"));
        assertEquals(1, bans("Alex"));
    }

    @Test
    void staffWithoutTheLimitPermission_isNotLimited() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve griefing");
        confirm(mod);

        assertEquals(1, bans("Steve"));
    }

    @Test
    void theLimitAlsoAppliesToPredefinedReasons() {
        server = TestServer.in(folder).start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.punish", "judicator.timelimit.helper"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "punish Steve Cheating"); // TEMPBAN of 7 months and 2 weeks
        assertTrue(helper.received("Your rank can apply a ban for at most 1 day."), helper.messages().toString());
        server.command(helper, "punish Steve Player Offense"); // TEMPMUTE of 7 days and 2 hours
        assertTrue(helper.received("Your rank can apply a mute for at most 1 week."), helper.messages().toString());

        assertTrue(server.punishments.all().isEmpty());
    }

    // --- cooldown ---

    @Test
    void aSecondStaffMember_cannotPunishTheSamePlayerRightAfter() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer mod2 = server.online(new FakePlayer("Mod2", "10.0.0.3", "judicator.tempban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve hacking");
        confirm(mod);
        server.command(mod2, "tempban Steve 1:d hacking");

        assertTrue(mod2.received("Steve received a ban 0s ago. Wait 30s before punishing again."), mod2.messages().toString());
        assertTrue(server.judicator.getPunishCache().getContext("Mod2").isEmpty());
        assertEquals(1, bans("Steve"));
    }

    @Test
    void twoConfirmationsOpenAtOnce_onlyTheFirstIsApplied() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer mod2 = server.online(new FakePlayer("Mod2", "10.0.0.3", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve hacking");
        server.command(mod2, "ban Steve hacking");
        confirm(mod);
        confirm(mod2);

        assertEquals(1, bans("Steve"));
        assertTrue(mod2.received("Steve received a ban"), mod2.messages().toString());
        assertFalse(mod2.received("The punishment has been successfully applied to the player."));
    }

    @Test
    void aPunishmentThatCouldNotBeStored_doesNotStartTheCooldown() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve " + "x".repeat(300)); // longer than the reason column: the database refuses it
        confirm(mod);
        assertTrue(mod.received("An unknown error occurred."), mod.messages().toString());
        assertEquals(0, bans("Steve"));

        server.command(mod, "ban Steve hacking");
        confirm(mod);
        assertEquals(1, bans("Steve"), "the retry is not blocked by a ban that does not exist");
    }

    @Test
    void aPlayerNamedConsole_doesNotTakeTheConsolesPlace() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.online(new FakePlayer("Console", "10.0.0.3"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve hacking");
        confirm(mod);
        server.command(server.console, "tempban Steve 3:d got three warns");

        assertEquals(2, bans("Steve"), "the console is never blocked by the cooldown");
    }

    @Test
    void theCooldownEnds() {
        server = TestServer.in(folder).start();
        final MutableClock clock = new MutableClock();
        server.judicator.getPunishCooldown().setClock(clock);
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.mute"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "mute Steve spam");
        confirm(mod);
        clock.advance(Duration.ofSeconds(29));
        server.command(mod, "mute Steve spam");
        assertTrue(mod.received("Wait 1s before punishing again."), mod.messages().toString());

        clock.advance(Duration.ofSeconds(1));
        server.command(mod, "mute Steve spam");
        confirm(mod);
        assertEquals(2, mutes("Steve"));
    }

    @Test
    void anotherKindOfPunishment_isNotBlocked() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.mute", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "mute Steve spam");
        confirm(mod);
        server.command(mod, "ban Steve hacking");
        confirm(mod);

        assertEquals(1, mutes("Steve"));
        assertEquals(1, bans("Steve"));
    }

    @Test
    void theConsole_isNotBlockedButStartsTheCooldown() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "tempban Steve 1:d hacking");
        server.command(server.console, "tempban Steve 3:d got three warns"); // e.g. the warn tree
        assertEquals(2, bans("Steve"));

        server.command(mod, "ban Steve hacking");
        assertTrue(mod.received("Steve received a ban"), mod.messages().toString());
        assertEquals(2, bans("Steve"));
    }

    @Test
    void warnsHaveTheirOwnCooldown() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.warn"));
        final FakePlayer mod2 = server.online(new FakePlayer("Mod2", "10.0.0.3", "judicator.warn"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "warn Steve spam");
        server.command(mod2, "warn Steve spam");

        assertTrue(mod2.received("Steve received a warn"), mod2.messages().toString());
        assertEquals(1, server.warns.findAllByUsername("Steve").size());
    }

    @Test
    void kicksHaveTheirOwnCooldownWhenEnabled() {
        server = TestServer.in(folder).set("cooldowns.kick", 10).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.kick"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "kick Steve spam");
        final FakePlayer steveAgain = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "kick Steve spam");

        assertTrue(mod.received("Steve received a kick"), mod.messages().toString());
        assertFalse(steveAgain.wasKicked());
    }

    @Test
    void zeroSeconds_turnsTheCooldownOff() {
        server = TestServer.in(folder).set("cooldowns.ban", 0).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer mod2 = server.online(new FakePlayer("Mod2", "10.0.0.3", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve hacking");
        confirm(mod);
        server.command(mod2, "ban Steve hacking");
        confirm(mod2);

        assertEquals(2, bans("Steve"));
    }

    @Test
    void anOldConfigWithoutTheNewKeys_keepsTheOldBehaviour() {
        server = TestServer.in(folder)
                .remove("cooldowns")
                .remove("time-limits")
                .start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.timelimit.helper"));
        final FakePlayer mod2 = server.online(new FakePlayer("Mod2", "10.0.0.3", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(helper, "ban Steve hacking");
        confirm(helper);
        server.command(mod2, "ban Steve hacking");
        confirm(mod2);

        assertEquals(2, bans("Steve"));
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
