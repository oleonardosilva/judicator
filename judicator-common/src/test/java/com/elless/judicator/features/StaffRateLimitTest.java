package com.elless.judicator.features;

import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * staff-rate-limit: a stolen or abusive staff account cannot punish everyone. One staff member waits between two
 * punishments of a kind, and going past the most allowed in the window locks them out of punishing until /staffunlock.
 */
class StaffRateLimitTest {

    @TempDir
    Path folder;

    private TestServer server;
    private final MutableClock clock = new MutableClock();

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private TestServer.Builder limited() {
        return TestServer.in(folder).set("staff-rate-limit.enabled", true);
    }

    private void start(TestServer.Builder builder) {
        server = builder.start();
        server.judicator.getStaffRateLimit().setClock(clock);
    }

    /**
     * Steve0, Steve1... online, so each ban has a target of its own (the per-target cooldown never gets in the way).
     */
    private void targets(int count) {
        for (int i = 0; i < count; i++) server.online(new FakePlayer("Steve" + i, "10.0.1." + i));
    }

    private boolean ban(FakePlayer staff, String target) {
        server.command(staff, "ban " + target + " hacking");
        if (server.judicator.getPunishCache().getContext(staff.getName()).isEmpty()) return false;
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(staff, "confirm"));
        return true;
    }

    private int bans(String name) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, PunishType.BAN, PunishType.TEMPBAN).size();
    }

    private int allBans() {
        return (int) server.punishments.all().stream().filter(p -> p.getType() == PunishType.BAN && p.getStatus() == PunishStatus.ACTIVE).count();
    }

    // --- delay ---

    @Test
    void aStaffMember_waitsBetweenTwoBans() {
        start(limited());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        targets(2);

        assertTrue(ban(mod, "Steve0"));
        assertFalse(ban(mod, "Steve1"), "refused before the confirmation");
        assertTrue(mod.received("Wait 10s before applying another ban."), mod.messages().toString());

        clock.advance(Duration.ofSeconds(9));
        assertFalse(ban(mod, "Steve1"));
        assertTrue(mod.received("Wait 1s before applying another ban."), mod.messages().toString());

        clock.advance(Duration.ofSeconds(1));
        assertTrue(ban(mod, "Steve1"));
        assertEquals(1, bans("Steve1"));
    }

    @Test
    void theDelay_isPerKindOfPunishment() {
        start(limited());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.mute"));
        targets(2);

        assertTrue(ban(mod, "Steve0"));
        server.command(mod, "mute Steve1 spam");
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(mod, "confirm"));

        assertEquals(1, server.punishments.findAllActiveByUsernameAndTypes("Steve1", PunishType.MUTE).size());
    }

    @Test
    void twoConfirmationsOpenAtOnce_onlyTheFirstPasses() {
        start(limited());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.tempban"));
        targets(2);

        server.command(mod, "ban Steve0 hacking");
        server.judicator.getStaffRateLimit().acquire(mod, "ban"); // another ban confirmed meanwhile
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(mod, "confirm"));

        assertEquals(0, bans("Steve0"));
        assertTrue(mod.received("Wait 10s before applying another ban."), mod.messages().toString());
    }

    @Test
    void kicksAndWarns_areLimitedToo() {
        start(limited());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.kick", "judicator.warn"));
        targets(4);

        server.command(mod, "kick Steve0 spam");
        server.command(mod, "kick Steve1 spam");
        assertTrue(mod.received("Wait 3s before applying another kick."), mod.messages().toString());
        assertTrue(server.platform.getPlayer("Steve1").isPresent(), "not kicked");

        server.command(mod, "warn Steve2 spam");
        server.command(mod, "warn Steve3 spam");
        assertTrue(mod.received("Wait 5s before applying another warn."), mod.messages().toString());
        assertEquals(1, server.warns.all().size());
    }

    @Test
    void aPunishmentThatCouldNotBeStored_doesNotCount() {
        start(limited());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        targets(1);

        server.command(mod, "ban Steve0 " + "x".repeat(300)); // longer than the reason column: the database refuses it
        server.chat(mod, "confirm");
        assertTrue(mod.received("An unknown error occurred."), mod.messages().toString());

        assertTrue(ban(mod, "Steve0"), "the retry is not delayed by a ban that does not exist");
        assertEquals(1, bans("Steve0"));
    }

    // --- max and lock ---

    private TestServer.Builder threeBansAtMost() {
        return limited().set("staff-rate-limit.delay.ban", 0).set("staff-rate-limit.max.ban", 3);
    }

    @Test
    void pastTheMax_theStaffMemberIsLockedAndTheStaffWarned() {
        start(threeBansAtMost());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.mute"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.ratelimit.notify"));
        targets(5);

        for (int i = 0; i < 3; i++) assertTrue(ban(mod, "Steve" + i));
        assertFalse(ban(mod, "Steve3"));

        assertEquals(3, allBans());
        assertTrue(mod.received("You applied 3 ban(s) in the last 10 minutes and can no longer punish."), mod.messages().toString());
        assertTrue(admin.received("[Alert] Mod applied 3 ban(s) in 10 minutes and was locked out of punishing."), admin.messages().toString());
        assertTrue(admin.clicks().contains("/staffrollback Mod 600:s"), admin.clicks().toString());

        clock.advance(Duration.ofHours(1));
        server.command(mod, "mute Steve4 spam");
        assertTrue(mod.received("You are locked out of punishing"), mod.messages().toString());
        assertTrue(server.judicator.getPunishCache().getContext("Mod").isEmpty(), "the lock outlasts the window and covers every kind");
    }

    @Test
    void theRollbackOfTheAlert_undoesWhatTheLockedStaffMemberApplied() {
        start(threeBansAtMost());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.admin", "judicator.ratelimit.notify"));
        targets(4);
        for (int i = 0; i < 4; i++) ban(mod, "Steve" + i);

        server.command(admin, "staffrollback Mod 600:s confirm");

        assertEquals(0, allBans());
    }

    @Test
    void anUnlockedStaffMember_canPunishAgainWithACleanCount() {
        start(threeBansAtMost());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.staffunlock", "judicator.ratelimit.notify"));
        targets(8);
        for (int i = 0; i < 4; i++) ban(mod, "Steve" + i);

        server.command(admin, "staffunlock");
        assertTrue(admin.received("Locked staff: Mod"), admin.messages().toString());
        server.command(admin, "staffunlock mod");
        assertTrue(admin.received("Mod can punish again."), admin.messages().toString());
        assertTrue(admin.received("[Staff] Admin unlocked Mod"), admin.messages().toString());
        assertTrue(mod.received("You can punish again."), mod.messages().toString());

        for (int i = 4; i < 7; i++) assertTrue(ban(mod, "Steve" + i));
        assertEquals(6, allBans());
        server.command(admin, "staffunlock Mod");
        assertTrue(admin.received("Mod is not locked."), admin.messages().toString());
    }

    @Test
    void aLockedStaffMember_cannotUnlockThemselves() {
        start(threeBansAtMost());
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.staffunlock"));
        targets(4);
        for (int i = 0; i < 4; i++) ban(mod, "Steve" + i);

        server.command(mod, "staffunlock Mod");

        assertTrue(mod.received("You cannot unlock yourself."), mod.messages().toString());
        assertEquals(List.of("Mod"), server.judicator.getStaffRateLimit().locked());
    }

    @Test
    void withoutLock_theStaffMemberOnlyWaitsForTheWindow() {
        start(threeBansAtMost().set("staff-rate-limit.lock", false));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.ratelimit.notify"));
        targets(4);

        for (int i = 0; i < 3; i++) {
            assertTrue(ban(mod, "Steve" + i));
            clock.advance(Duration.ofMinutes(1));
        }
        assertFalse(ban(mod, "Steve3"));
        assertTrue(mod.received("You applied 3 ban(s) in the last 10 minutes. Wait 420s before applying another."), mod.messages().toString());
        assertFalse(admin.received("[Alert]"), admin.messages().toString());

        clock.advance(Duration.ofSeconds(420));
        assertTrue(ban(mod, "Steve3"));
    }

    // --- who is limited ---

    @Test
    void adminsAreLimited_theConsoleAndTheExplicitBypassAreNot() {
        start(threeBansAtMost());
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.2", "judicator.admin", "judicator.ban"));
        final FakePlayer trusted = server.online(new FakePlayer("Owner", "10.0.0.3", "judicator.ban", "judicator.ratelimit.bypass"));
        targets(12);

        for (int i = 0; i < 4; i++) ban(admin, "Steve" + i);
        for (int i = 4; i < 8; i++) assertTrue(ban(trusted, "Steve" + i));
        for (int i = 8; i < 12; i++) server.command(server.console, "ban Steve" + i + " hacking");

        assertEquals(11, allBans());
        assertEquals(List.of("Admin"), server.judicator.getStaffRateLimit().locked());
    }

    @Test
    void disabled_nobodyIsLimited() {
        start(threeBansAtMost().set("staff-rate-limit.enabled", false));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        targets(5);

        for (int i = 0; i < 5; i++) assertTrue(ban(mod, "Steve" + i));

        assertEquals(5, allBans());
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
