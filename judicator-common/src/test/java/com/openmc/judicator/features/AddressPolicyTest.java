package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * exempt-addresses (shared networks that IP punishments and alt warnings leave alone) and ip-limits (accounts per IP).
 */
class AddressPolicyTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private TestServer.Builder withSchoolNetwork() {
        return TestServer.in(folder).set("exempt-addresses", List.of("127.0.0.1", "::1", "10.20.0.0/16"));
    }

    // --- exempt addresses ---

    @Test
    void ipPunishmentOnAnExemptAddress_isRefusedForStaffAndConsole() {
        server = withSchoolNetwork().start();
        server.online(new FakePlayer("Student", "10.20.3.4"));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban.ip", "judicator.mute.ip"));

        server.command(mod, "banip Student griefing");
        server.command(server.console, "muteip Student spam");
        server.command(server.console, "tempbanip Student 1:d got three warns");

        assertTrue(mod.received("This IP address is in exempt-addresses and cannot be punished by IP."), mod.messages().toString());
        assertTrue(server.console.received("cannot be punished by IP"));
        assertTrue(server.judicator.getPunishCache().getContext("Mod").isEmpty());
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void accountPunishmentsStillWorkOnAnExemptAddress() {
        server = withSchoolNetwork().start();
        final FakePlayer student = server.online(new FakePlayer("Student", "10.20.3.4"));
        server.online(new FakePlayer("Classmate", "10.20.3.4"));

        server.command(server.console, "ban Student griefing");

        assertTrue(student.wasKicked());
        assertTrue(server.join(new FakePlayer("Classmate2", "10.20.3.4")).isEmpty(), "the rest of the school can still join");
    }

    @Test
    void ipPunishmentOfAnOfflinePlayerWhoseLastAddressIsExempt_isRefused() {
        server = withSchoolNetwork().start();
        final FakePlayer student = server.online(new FakePlayer("Student", "10.20.3.4"));
        server.quit(student);

        server.command(server.console, "banip Student griefing");

        assertTrue(server.console.received("cannot be punished by IP"));
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void addressesOutsideTheList_areStillIpPunished() {
        server = withSchoolNetwork().start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "banip Steve griefing");

        assertEquals(1, server.punishments.findAllActiveByUsernameAndTypes("Steve", PunishType.BAN).size());
    }

    @Test
    void altsIgnoreExemptAddresses() {
        server = withSchoolNetwork().start();
        final FakePlayer staff = server.online(new FakePlayer("Staff", "10.0.0.9", "judicator.alts.notify"));
        final FakePlayer banned = server.online(new FakePlayer("Banned", "10.20.3.4"));
        server.online(new FakePlayer("Friend", "10.0.0.5"));
        server.command(server.console, "ban Banned hacking");
        server.quit(banned);

        server.online(new FakePlayer("Student", "10.20.3.4"));
        assertFalse(staff.received("[Alts]"), "a shared network does not make every student an alt: " + staff.messages());

        server.command(server.console, "alts Student");
        assertTrue(server.console.received("No other accounts were found for Student."), server.console.messages().toString());
    }

    @Test
    void altsStillLinkAccountsOnOtherAddresses_andLinesOpenTheHistory() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("SteveAlt", "10.0.0.1"));

        server.command(server.console, "alts Steve");

        assertTrue(server.console.received("stevealt"), server.console.messages().toString());
        assertTrue(server.console.clicks().contains("/phistory stevealt"), server.console.clicks().toString());
    }

    @Test
    void anIpPunishmentStoredOnAnExemptAddress_doesNotReachEveryoneBehindIt() {
        server = TestServer.in(folder).start();
        final LocalDateTime now = LocalDateTime.now();
        // e.g. imported from a plugin that recorded the proxy's address
        server.punishments.save(Punishment.create(null, "old", "Console", "Old", "127.0.0.1", now.minusDays(1), null,
                List.of(), PunishType.BAN));

        assertTrue(server.join(new FakePlayer("Fresh", "127.0.0.1")).isEmpty());
        assertTrue(server.join(new FakePlayer("Old", "127.0.0.1")).isPresent(), "the account it was recorded on stays banned");
    }

    @Test
    void invalidEntriesAreIgnored() {
        server = TestServer.in(folder).set("exempt-addresses", List.of("127.0.0.1", "school.example.com", "10.0.0.0/99")).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "banip Steve griefing");

        assertEquals(1, server.punishments.all().size(), "nothing became exempt by mistake");
    }

    // --- account limits ---

    @Test
    void maxOnline_refusesOneAccountTooMany() {
        server = TestServer.in(folder).set("ip-limits.max-online", 2).start();
        server.online(new FakePlayer("One", "10.0.0.1"));
        final FakePlayer two = server.online(new FakePlayer("Two", "10.0.0.1"));

        final Optional<String> third = server.join(new FakePlayer("Three", "10.0.0.1"));
        assertTrue(third.isPresent() && third.get().contains("Too many accounts are connecting from your network."), third.toString());

        assertTrue(server.join(new FakePlayer("Two", "10.0.0.1")).isEmpty(), "the same account reconnecting is not one more");
        assertTrue(server.join(new FakePlayer("Other", "10.0.0.2")).isEmpty(), "other addresses are not affected");
        server.quit(two);
        assertTrue(server.join(new FakePlayer("Three", "10.0.0.1")).isEmpty(), "room again after someone leaves");
    }

    @Test
    void maxRecentAccounts_refusesNewAccountsWithinThePeriod() {
        server = TestServer.in(folder).set("ip-limits.max-recent-accounts", 3).start();
        final MutableClock clock = new MutableClock();
        server.judicator.getAddressPolicy().setClock(clock);
        for (String name : new String[]{"Bot1", "Bot2", "Bot3"}) {
            server.quit(server.online(new FakePlayer(name, "10.0.0.1")));
        }

        assertTrue(server.join(new FakePlayer("Bot4", "10.0.0.1")).isPresent(), "a 4th different account in one day");
        assertTrue(server.join(new FakePlayer("Bot1", "10.0.0.1")).isEmpty(), "an account already seen in the period may come back");

        clock.advance(Duration.ofHours(25));
        assertTrue(server.join(new FakePlayer("Bot4", "10.0.0.1")).isEmpty(), "after the period the count starts again");
    }

    @Test
    void namesThatNeverLogIn_doNotUseUpTheRecentAccounts() {
        server = TestServer.in(folder).set("ip-limits.max-recent-accounts", 2).start();
        // On Velocity the pre-login runs before authentication: anyone can send any name there.
        for (String fake : new String[]{"fake1", "fake2", "fake3"}) {
            assertTrue(server.judicator.getConnectionHandler().checkPreLogin(fake, "10.0.0.1").isEmpty());
        }

        assertTrue(server.join(new FakePlayer("RealOne", "10.0.0.1")).isEmpty());
        assertTrue(server.join(new FakePlayer("RealTwo", "10.0.0.1")).isEmpty());
        assertTrue(server.join(new FakePlayer("RealThree", "10.0.0.1")).isPresent(), "only accounts that got in count");
    }

    @Test
    void aLoginRefusedByABan_doesNotUseUpTheRecentAccounts() {
        server = TestServer.in(folder).set("ip-limits.max-recent-accounts", 1).start();
        final FakePlayer banned = new FakePlayer("Banned", "10.0.0.9");
        server.online(banned);
        server.command(server.console, "ban Banned hacking");

        assertTrue(server.join(new FakePlayer("Banned", "10.0.0.1")).isPresent(), "refused by the ban");
        assertTrue(server.join(new FakePlayer("Legit", "10.0.0.1")).isEmpty());
    }

    @Test
    void exemptAddressesAreNeverLimited() {
        server = withSchoolNetwork().set("ip-limits.max-online", 1).start();
        server.online(new FakePlayer("Student1", "10.20.3.4"));
        assertTrue(server.join(new FakePlayer("Student2", "10.20.3.4")).isEmpty());
    }

    @Test
    void aBannedAccountSeesItsBanNotTheLimit() {
        server = TestServer.in(folder).set("ip-limits.max-online", 1).start();
        server.online(new FakePlayer("One", "10.0.0.1"));
        server.command(server.console, "ban Two hacking");

        final Optional<String> kick = server.join(new FakePlayer("Two", "10.0.0.1"));
        assertTrue(kick.isPresent() && kick.get().contains("You have been banned from the server."), kick.toString());
    }

    @Test
    void anOldConfigWithoutTheNewKeys_limitsNothingAndExemptsNothing() {
        server = TestServer.in(folder)
                .remove("exempt-addresses")
                .remove("ip-limits")
                .start();
        for (int i = 0; i < 5; i++) server.online(new FakePlayer("Local" + i, "127.0.0.1"));

        server.command(server.console, "banip Local0 griefing");

        assertEquals(1, server.punishments.all().size());
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
 