package com.elless.judicator.features;

import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.platform.TeleportOutcome;
import com.elless.judicator.reports.Report;
import com.elless.judicator.reports.ReportState;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /report and /reports: players report, the staff hears of it, takes it, closes it, and the reporter hears back.
 */
class ReportsTest {

    private static final String[] STAFF = {"judicator.reports", "judicator.reports.manage", "judicator.reports.tp", "judicator.reports.notify"};

    @TempDir
    Path folder;

    private final List<TestServer> servers = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(TestServer::close);
    }

    private TestServer start(TestServer.Builder builder) {
        final TestServer server = builder.start();
        servers.add(server);
        return server;
    }

    private TestServer start() {
        return start(TestServer.in(folder));
    }

    private Report only(TestServer server) {
        final List<Report> all = server.reports.all();
        assertEquals(1, all.size(), all.toString());
        return all.get(0);
    }

    @Test
    void aPlayerReportsAnother_andTheStaffHearsOfIt() {
        final TestServer server = start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.9", STAFF));
        final FakePlayer plain = server.online(new FakePlayer("Plain", "10.0.0.8"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(alex, "report steve flying with a hack");

        final Report report = only(server);
        assertEquals("Steve", report.getTargetName(), "the name as the player is online");
        assertEquals("flying with a hack", report.getReason());
        assertEquals(ReportState.OPEN, report.getState());
        assertTrue(alex.received("Thank you: your report #" + report.getId() + " about Steve was sent to the staff."), alex.messages().toString());
        assertTrue(mod.received("[Reports] Alex reported Steve: flying with a hack (#" + report.getId() + ")"), mod.messages().toString());
        assertFalse(plain.received("[Reports]"));
        assertTrue(mod.clicks().contains("/reports info " + report.getId()), mod.clicks().toString());
    }

    @Test
    void reportsAreRefused_forOneselfAnExemptPlayerAndSomeoneOffline() {
        final TestServer server = start(TestServer.in(folder).set("reports.cooldown", 0)
                .immunePlayers("  Owner: [report]\n  Admin: [all]\n"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Owner", "10.0.0.3"));
        server.online(new FakePlayer("Admin", "10.0.0.4"));

        server.command(alex, "report Alex x");
        server.command(alex, "report Owner x");
        server.command(alex, "report Nobody x");
        server.command(alex, "report Admin abuse");

        assertTrue(alex.received("You cannot report yourself."));
        assertTrue(alex.received("This player cannot be reported."));
        assertTrue(alex.received("Nobody is not online."));
        assertEquals("Admin", only(server).getTargetName(), "an exemption from punishments is not one from reports");
        server.command(server.console, "report Admin x");
        assertTrue(server.console.received("Only players can do this."));
    }

    @Test
    void aPlayerWaitsBetweenReports_evenWhenTheFirstWasRefusedForAnExemptPlayer() {
        final TestServer server = start(TestServer.in(folder).immunePlayers("  Owner: [report]\n"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Owner", "10.0.0.3"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(alex, "report Owner x");
        server.command(alex, "report Steve x");

        assertTrue(alex.received("This player cannot be reported."));
        assertTrue(alex.received("Wait 60s before reporting again."), "finding out who is exempt costs the cooldown: " + alex.messages());
        assertTrue(server.reports.all().isEmpty());
    }

    @Test
    void theReporterTextIsShownAsTyped_neverAsPlaceholdersOrColours() {
        final TestServer server = start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.9", STAFF));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(alex, "report Steve says {assignee} and §kthis");

        assertEquals("says {assignee} and kthis", only(server).getReason());
        assertTrue(mod.received("[Reports] Alex reported Steve: says {assignee} and kthis"), mod.messages().toString());
    }

    @Test
    void aReportOnlyGoesToSomeoneWhoMayReadReports() {
        final TestServer server = start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.9", STAFF));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");
        final long id = only(server).getId();

        server.command(mod, "reports assign " + id + " Steve");

        assertTrue(mod.received("Steve cannot handle reports."), mod.messages().toString());
        assertEquals(ReportState.OPEN, only(server).getState());
        assertFalse(steve.received("[Reports]"), "the reported player never learns who reported them");
    }

    @Test
    void clearShowsTheReportsThePlayerMadeToo() {
        final TestServer server = start();
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");

        server.command(server.console, "judicator clear Alex");

        assertTrue(server.console.received("1 report(s) made or received"), server.console.messages().toString());
    }

    @Test
    void theSamePlayerIsNotReportedTwiceWhileTheReportIsOpen() {
        final TestServer server = start(TestServer.in(folder).set("reports.cooldown", 0));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(alex, "report Steve x");
        server.command(alex, "report Steve again");

        assertTrue(alex.received("You already have an open report about Steve."), alex.messages().toString());
        server.command(server.console, "reports close " + only(server).getId());
        server.command(alex, "report Steve once more");
        assertEquals(2, server.reports.all().size());
    }

    @Test
    void anOfflinePlayer_canBeReportedWithThePermission_ifTheirAccountIsKnown() {
        final TestServer server = start();
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2", "judicator.report.offline"));
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));

        server.command(alex, "report Steve x");
        server.command(alex, "report Typo x");

        assertEquals("Steve", only(server).getTargetName());
        assertTrue(alex.received("No player called Typo was found."), alex.messages().toString());
    }

    @Test
    void withRequirePermission_onlyPlayersWithItMayReport() {
        final TestServer server = start(TestServer.in(folder).set("reports.require-permission", true));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        final FakePlayer trusted = server.online(new FakePlayer("Trusted", "10.0.0.3", "judicator.report"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertFalse(server.command(alex, "report Steve x"));
        assertTrue(server.command(trusted, "report Steve x"));
    }

    @Test
    void theStaffTakesGivesBackAndClosesAReport_andTheReporterHearsBack() {
        final TestServer server = start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.9", STAFF));
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.8", STAFF));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");
        final long id = only(server).getId();

        server.command(mod, "reports assign " + id + " Helper");
        assertEquals(ReportState.ASSIGNED, only(server).getState());
        assertEquals("Helper", only(server).getAssigneeName().orElseThrow());
        assertTrue(helper.received("[Reports] Mod gave you report #" + id + " about Steve."), helper.messages().toString());
        assertTrue(alex.received("Your report #" + id + " about Steve is being handled by Helper."), alex.messages().toString());

        server.command(mod, "reports unassign " + id);
        assertEquals(ReportState.OPEN, only(server).getState());
        server.command(mod, "reports unassign " + id);
        assertTrue(mod.received("Report #" + id + " is not assigned to anyone."));

        server.command(mod, "reports close " + id + " banned for fly");
        final Report closed = only(server);
        assertEquals(ReportState.CLOSED, closed.getState());
        assertEquals("banned for fly", closed.getCloseComment().orElseThrow());
        assertTrue(alex.received("Your report #" + id + " about Steve was closed by Mod: banned for fly"), alex.messages().toString());
        server.command(mod, "reports close " + id);
        assertTrue(mod.received("Report #" + id + " is already closed."));
        server.command(mod, "reports assign " + id);
        assertTrue(mod.received("Report #" + id + " is already closed."));
    }

    @Test
    void managingReports_needsItsPermission() {
        final TestServer server = start();
        final FakePlayer viewer = server.online(new FakePlayer("Viewer", "10.0.0.9", "judicator.reports"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");
        final long id = only(server).getId();

        server.command(viewer, "reports info " + id);
        assertTrue(viewer.received("Report #" + id), viewer.messages().toString());
        assertFalse(viewer.clicks().contains("/reports assign " + id), "no button for what they cannot do");
        server.command(viewer, "reports close " + id);
        server.command(viewer, "reports tp " + id);

        assertTrue(viewer.received("You do not have permission to do this."));
        assertEquals(ReportState.OPEN, only(server).getState());
        assertFalse(server.command(alex, "reports"), "players do not see the reports");
    }

    @Test
    void theListShowsOpenReportsByDefault_byStateAndInPages() {
        final TestServer server = start(TestServer.in(folder).set("reports.cooldown", 0));
        final List<FakePlayer> reporters = new ArrayList<>();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        for (int i = 0; i < 12; i++) reporters.add(server.online(new FakePlayer("Player" + i, "10.0.1." + i)));
        reporters.forEach(player -> server.command(player, "report Steve spam"));
        server.command(server.console, "reports close " + server.reports.all().get(0).getId());
        server.console.clearMessages();

        server.command(server.console, "reports");
        assertTrue(server.console.received("Reports: open (11 in total, page 1/2)"), server.console.messages().toString());
        server.command(server.console, "reports list closed");
        assertTrue(server.console.received("Reports: closed (1 in total, page 1/1)"), server.console.messages().toString());
        server.command(server.console, "reports list all 2");
        assertTrue(server.console.received("Reports: all (12 in total, page 2/2)"), server.console.messages().toString());
        server.command(server.console, "reports list weird");
        assertTrue(server.console.received("Unknown list 'weird'"), server.console.messages().toString());
        server.command(server.console, "reports info abc");
        assertTrue(server.console.received("'abc' is not a report id."));
        server.command(server.console, "reports info 999");
        assertTrue(server.console.received("Report #999 not found."));
    }

    @Test
    void tp_takesTheStaffToTheReportedPlayer() {
        final TestServer server = start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.9", STAFF));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");
        final long id = only(server).getId();

        server.command(mod, "reports tp " + id);
        assertEquals(List.of("Mod -> Steve"), server.platform.teleports());
        assertFalse(mod.received("You are on the server of Steve"));

        // a proxy whose backend did not teleport (no Judicator there)
        server.platform.teleportOutcome(TeleportOutcome.SERVER_ONLY);
        server.command(mod, "reports tp " + id);
        assertTrue(mod.received("You are on the server of Steve, but it did not teleport you to them"), mod.messages().toString());

        server.quit(steve);
        server.command(mod, "reports tp " + id);
        assertTrue(mod.received("Steve is not online."), mod.messages().toString());
        server.command(server.console, "reports tp " + id);
        assertTrue(server.console.received("Only players can do this."));
    }

    @Test
    void staffJoiningHearsHowManyReportsAreOpen() {
        // on Velocity the notice waits for the first server (VelocityListener.onServerPostConnect)
        final TestServer server = start(TestServer.in(folder).platform(PlatformType.BUKKIT));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");

        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.9", STAFF));
        final FakePlayer plain = server.online(new FakePlayer("Plain", "10.0.0.8"));

        assertTrue(mod.received("[Reports] There are 1 open report(s)."), mod.messages().toString());
        assertFalse(plain.received("[Reports]"));
    }

    @Test
    void reportsReachTheStaffOfTheOtherServers_andTheReporterHearsWhereTheyAre() {
        final TestServer a = start(TestServer.in(folder.resolve("a")).set("sync.enabled", true));
        final TestServer b = start(TestServer.in(folder.resolve("b")).set("sync.enabled", true).sharingDatabaseWith(a));
        final FakePlayer alex = a.online(new FakePlayer("Alex", "10.0.0.2"));
        a.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer mod = b.online(new FakePlayer("Mod", "10.0.0.9", STAFF));

        a.command(alex, "report Steve x");
        b.pollSync();
        final long id = only(a).getId();
        assertTrue(mod.received("[Reports] Alex reported Steve: x (#" + id + ")"), mod.messages().toString());

        // taken and closed before the reporter's server reads either notice: they hear the outcome once
        b.command(mod, "reports assign " + id);
        b.command(mod, "reports close " + id + " handled");
        a.pollSync();
        a.pollSync();
        assertEquals(1, alex.messages().stream().filter(text -> text.contains("was closed by Mod")).count(), alex.messages().toString());
    }

    @Test
    void theCooldownCountsAReportMadeOnAnotherServerOrBeforeARestart() {
        final TestServer a = start(TestServer.in(folder.resolve("a")));
        final TestServer b = start(TestServer.in(folder.resolve("b")).sharingDatabaseWith(a));
        a.online(new FakePlayer("Steve", "10.0.0.1"));
        b.online(new FakePlayer("Bob", "10.0.0.3"));
        a.command(a.online(new FakePlayer("Alex", "10.0.0.2")), "report Steve x");

        final FakePlayer alexOnB = b.online(new FakePlayer("Alex", "10.0.0.2"));
        b.command(alexOnB, "report Bob y");

        assertTrue(alexOnB.messages().stream().anyMatch(text -> text.startsWith("Wait ")), alexOnB.messages().toString());
        assertEquals(1, a.reports.all().size());
    }

    private static Report storedReportBy(FakePlayer reporter, LocalDateTime createdAt) {
        final Report report = Report.create(reporter.getUniqueId(), reporter.getName(), null, "Bob", "old", null);
        report.setState(ReportState.CLOSED);
        report.setCreatedAt(createdAt);
        report.setClosedAt(createdAt);
        return report;
    }

    @Test
    void aRefusalBecauseOfAStoredReport_doesNotStartANewWait() {
        final TestServer server = start(TestServer.in(folder));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.reports.insert(storedReportBy(alex, LocalDateTime.now().minusSeconds(10)));
        server.command(alex, "report Steve x");
        assertTrue(alex.received("Wait 50s before reporting again."), alex.messages().toString());

        // that report no longer counts (as if its wait were over): the refused attempt does not hold Alex back
        server.reports.deleteClosedBefore(LocalDateTime.now().plusMinutes(1));
        server.command(alex, "report Steve x");

        assertEquals(List.of("x"), server.reports.all().stream().map(Report::getReason).toList(), alex.messages().toString());
    }

    @Test
    void aStoredReportFromAServerWithItsClockAhead_neverMeansWaitingLongerThanTheCooldown() {
        final TestServer server = start(TestServer.in(folder));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        server.reports.insert(storedReportBy(alex, LocalDateTime.now().plusHours(1)));

        server.command(alex, "report Steve x");

        assertTrue(alex.received("Wait 60s before reporting again."), alex.messages().toString());
    }

    @Test
    void playerInfoCountsTheReports_andClearDeletesThem() {
        final TestServer server = start(TestServer.in(folder).set("reports.cooldown", 0));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.2"));
        final FakePlayer bob = server.online(new FakePlayer("Bob", "10.0.0.3"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(alex, "report Steve x");
        server.command(bob, "report Steve y");

        server.command(server.console, "playerinfo Steve");
        assertTrue(server.console.received("Reported: 2 time(s)"), server.console.messages().toString());

        server.command(server.console, "judicator clear Steve confirm");
        assertTrue(server.reports.all().isEmpty());
    }
}
