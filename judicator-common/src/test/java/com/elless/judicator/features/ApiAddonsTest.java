package com.elless.judicator.features;

import com.elless.judicator.api.ActionResult;
import com.elless.judicator.api.ActionStatus;
import com.elless.judicator.api.JudicatorApi;
import com.elless.judicator.api.event.JudicatorEvent;
import com.elless.judicator.api.event.NoteAddedEvent;
import com.elless.judicator.api.event.NoteDeletedEvent;
import com.elless.judicator.api.event.ReportChangedEvent;
import com.elless.judicator.api.event.ReportCreatedEvent;
import com.elless.judicator.api.jail.JailDefinition;
import com.elless.judicator.api.jail.JailEscape;
import com.elless.judicator.api.jail.JailPoint;
import com.elless.judicator.api.jail.JailView;
import com.elless.judicator.api.note.NoteView;
import com.elless.judicator.api.player.AccountStatus;
import com.elless.judicator.api.player.AccountView;
import com.elless.judicator.api.report.ReportStatus;
import com.elless.judicator.api.report.ReportView;
import com.elless.judicator.api.staff.StaffActivity;
import com.elless.judicator.platform.Location;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What judicator-api gives addons beyond punishments: reports, staff notes, accounts and alts, what the staff did, and
 * jails made from code.
 */
class ApiAddonsTest {

    @TempDir
    Path folder;

    private TestServer server;
    private JudicatorApi api;
    private final List<JudicatorEvent> events = new ArrayList<>();

    @BeforeEach
    void start() {
        server = TestServer.in(folder).platform(PlatformType.BUKKIT).start();
        api = server.judicator.getApi();
        api.getEventBus().subscribe(JudicatorEvent.class, events::add);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private <T extends JudicatorEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    // --- reports ---

    @Test
    void aPluginReports_theStaffHears_andTheReportIsClosedThroughTheApi() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.reports.notify"));

        final ActionResult<ReportView> created = api.report("AntiCheat", "Steve", "fly (vl 12)").join();

        assertEquals(ActionStatus.SUCCESS, created.status());
        final ReportView report = created.value().orElseThrow();
        assertEquals("AntiCheat", report.reporterName());
        assertEquals(ReportStatus.OPEN, report.status());
        assertTrue(staff.messages().stream().anyMatch(message -> message.contains("Steve")), staff.messages().toString());
        assertEquals(report.id(), events(ReportCreatedEvent.class).get(0).report().id());
        assertEquals(ActionStatus.ALREADY_REPORTED, api.report("AntiCheat", "Steve", "again").join().status());
        assertEquals(1, api.countReports(EnumSet.of(ReportStatus.OPEN)).join());
        assertEquals(1, api.countReportsAgainst("steve").join());
        assertEquals(report.id(), api.getReports(Set.of(ReportStatus.OPEN, ReportStatus.ASSIGNED), 10, 0).join().get(0).id());

        final ActionResult<ReportView> closed = api.closeReport(report.id(), "banned", "AntiCheat").join();

        assertEquals(ReportStatus.CLOSED, closed.value().orElseThrow().status());
        assertEquals(Optional.of("banned"), closed.value().orElseThrow().closeComment());
        assertEquals("AntiCheat", events(ReportChangedEvent.class).get(0).changedBy());
        assertEquals(ActionStatus.ALREADY_CLOSED, api.closeReport(report.id(), null, "AntiCheat").join().status());
        assertEquals(ActionStatus.NOT_FOUND, api.closeReport(999, null, "AntiCheat").join().status());
    }

    @Test
    void aReportByAPlayerPostsItsEvents() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alex = server.online(new FakePlayer("Alex", "10.0.0.3"));
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.reports", "judicator.reports.manage"));

        server.command(alex, "report Steve flying");
        final long id = events(ReportCreatedEvent.class).get(0).report().id();
        server.command(staff, "reports assign " + id);

        assertEquals(ReportStatus.ASSIGNED, events(ReportChangedEvent.class).get(0).report().status());
        assertEquals("Mod", events(ReportChangedEvent.class).get(0).changedBy());
    }

    // --- notes ---

    @Test
    void notesAreWrittenReadAndDeleted_withTheirEvents() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.admin"));

        final NoteView note = api.addNote("Steve", "watch his trades", "Economy").join().value().orElseThrow();
        server.command(staff, "note Steve asked for a refund");

        final List<NoteView> notes = api.getNotes("Steve").join();
        assertEquals(List.of("asked for a refund", "watch his trades"), notes.stream().map(NoteView::content).toList());
        assertEquals("Economy", notes.get(1).author());
        assertTrue(notes.get(0).authorUniqueId().isPresent(), "a staff member's UUID");
        assertEquals(2, events(NoteAddedEvent.class).size());

        assertEquals(ActionStatus.SUCCESS, api.deleteNote(note.id(), "Economy").join().status());
        server.command(staff, "delnote " + notes.get(0).id());
        assertTrue(api.getNotes("Steve").join().isEmpty());
        assertEquals(List.of("Economy", "Mod"), events(NoteDeletedEvent.class).stream().map(NoteDeletedEvent::deletedBy).toList());
        assertEquals(ActionStatus.NOT_FOUND, api.deleteNote(note.id(), "Economy").join().status());
        assertThrows(IllegalArgumentException.class, () -> api.addNote("Steve", " ", "Economy"));
    }

    // --- accounts and staff ---

    @Test
    void accountsTheirStatusAndAlts() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        api.mute("SteveAlt", "spam", "Plugin").join();

        assertEquals(AccountStatus.CLEAN, api.getAccountStatus("Steve").join());
        assertEquals(AccountStatus.MUTED, api.getAccountStatus("SteveAlt").join());
        final List<AccountView> alts = api.getAlts(steve.getUniqueId()).join();
        assertEquals(1, alts.size());
        assertEquals("SteveAlt", alts.get(0).name());
        assertEquals(AccountStatus.MUTED, alts.get(0).status());
        assertEquals("Steve", api.getPlayer(steve.getUniqueId()).join().orElseThrow().name());
    }

    @Test
    void whatTheStaffApplied() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.2"));
        final Instant before = Instant.now().minusSeconds(5);
        api.mute("Steve", "spam", "Bot").join();
        api.tempban("Alex", Duration.ofDays(1), "x-ray", "Bot").join();
        api.warn("Steve", "caps", "Bot").join();

        final StaffActivity bot = api.getStaffActivity(before).join().get(0);
        assertEquals("Bot", bot.staff());
        assertEquals(3, bot.total());
        assertEquals(1, bot.counts().get("WARN"));
        assertEquals(2, api.getPunishmentsBy("Bot", before, 10, 0).join().size());
        assertTrue(api.getPunishmentsBy("Bot", Instant.now().plusSeconds(60), 10, 0).join().isEmpty());
    }

    // --- jails made from code ---

    @Test
    void aJailMadeByAPlugin_holdsPrisoners_andCanBeReadAndDeleted() {
        final JailDefinition definition = new JailDefinition("vault", Optional.empty(), JailPoint.of("world", 10.5, 64, 10.5),
                JailPoint.of("world", 5, 60, 5), JailPoint.of("world", 15, 70, 15), Optional.of(JailPoint.of("world", 0.5, 64, 0.5)),
                false, 0, List.of(new JailEscape(JailPoint.of("world", 14.5, 64, 14.5), 1, 25)));

        final JailView saved = api.saveJail(definition, "Prisons").join().value().orElseThrow();

        assertEquals("vault", saved.name());
        assertEquals(1, saved.room());
        assertEquals(definition, api.getJail("vault").join().orElseThrow());
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        api.jail("Steve", "griefing", "Prisons").join();
        assertEquals(new Location("world", 10.5, 64, 10.5, 0, 0), server.platform.where(steve));
        assertEquals(ActionStatus.IN_USE, api.deleteJail("vault").join().status());

        api.unjail("Steve", null, "Prisons").join();
        assertEquals(new Location("world", 0.5, 64, 0.5, 0, 0), server.platform.where(steve));
        assertEquals(ActionStatus.SUCCESS, api.deleteJail("vault").join().status());
        assertEquals(ActionStatus.NOT_FOUND, api.deleteJail("vault").join().status());
        assertTrue(api.getJails().join().isEmpty());
    }

    @Test
    void anImpossibleJailIsRefusedWhenBuilt() {
        assertThrows(IllegalArgumentException.class, () -> new JailDefinition("vault", Optional.empty(), JailPoint.of("world", 50, 64, 50),
                JailPoint.of("world", 5, 60, 5), JailPoint.of("world", 15, 70, 15), Optional.empty(), true, 0, List.of()),
                "the spawn out of the area");
        assertThrows(IllegalArgumentException.class, () -> new JailDefinition("vault", Optional.empty(), JailPoint.of("world", 10, 64, 10),
                JailPoint.of("world", 5, 60, 5), JailPoint.of("world", 15, 70, 15), Optional.empty(), true, 0,
                List.of(new JailEscape(JailPoint.of("world", 11, 64, 10), 2, 50))), "an escape route over the spawn");
        assertThrows(IllegalArgumentException.class, () -> new JailDefinition("Vault Two", Optional.empty(), JailPoint.of("world", 10, 64, 10),
                JailPoint.of("world", 5, 60, 5), JailPoint.of("world", 15, 70, 15), Optional.empty(), true, 0, List.of()));
        assertFalse(api.getJail("vault").join().isPresent());
    }
}
