package com.elless.judicator.features;

import com.elless.judicator.api.JudicatorApi;
import com.elless.judicator.api.event.JudicatorEvent;
import com.elless.judicator.api.event.PlayerKickedEvent;
import com.elless.judicator.api.event.PunishmentAppliedEvent;
import com.elless.judicator.api.event.PunishmentEditedEvent;
import com.elless.judicator.api.event.PunishmentRevokedEvent;
import com.elless.judicator.api.event.Subscription;
import com.elless.judicator.api.event.WarnAppliedEvent;
import com.elless.judicator.api.event.WarnRevokedEvent;
import com.elless.judicator.api.punishment.PunishmentStatus;
import com.elless.judicator.api.punishment.PunishmentType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * judicator-api events: posted once, after the change is stored, for commands and API calls alike; a failing listener
 * never stops a punishment; only the server where the change was made posts them.
 */
class ApiEventsTest {

    @TempDir
    Path folder;

    private final List<TestServer> servers = new ArrayList<>();
    private final List<JudicatorEvent> events = new ArrayList<>();

    private TestServer start(TestServer.Builder builder) {
        final TestServer server = builder.start();
        servers.add(server);
        return server;
    }

    private TestServer start() {
        final TestServer server = start(TestServer.in(folder));
        server.judicator.getApi().getEventBus().subscribe(JudicatorEvent.class, events::add);
        return server;
    }

    @AfterEach
    void stop() {
        servers.forEach(TestServer::close);
    }

    private <T extends JudicatorEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void consoleBan_postsOneAppliedEventWithTheStoredPunishment() {
        final TestServer server = start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "ban Steve -s hacking");

        assertEquals(1, events.size(), events.toString());
        final PunishmentAppliedEvent applied = assertInstanceOf(PunishmentAppliedEvent.class, events.get(0));
        assertTrue(applied.silent());
        assertEquals(PunishmentType.BAN, applied.punishment().type());
        assertEquals("Steve", applied.punishment().targetName());
        assertEquals("hacking", applied.punishment().reason());
        assertEquals("Console", applied.punishment().punisher());
        assertEquals(server.punishments.all().get(0).getId(), applied.punishment().id());
    }

    @Test
    void staffPunishment_postsOnlyOnceConfirmed() {
        final TestServer server = start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.mute"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "mute Steve flood");
        assertTrue(events.isEmpty(), "nothing is stored before confirming");
        server.chat(mod, "https://imgur.com/proof");
        server.chat(mod, "confirm");

        final List<PunishmentAppliedEvent> applied = events(PunishmentAppliedEvent.class);
        assertEquals(1, applied.size());
        assertEquals("Mod", applied.get(0).punishment().punisher());
        assertEquals(List.of("https://imgur.com/proof"), applied.get(0).punishment().evidences());
    }

    @Test
    void apiPunishment_postsTheSameEventAsACommand() {
        final TestServer server = start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.judicator.getApi().tempban("Steve", Duration.ofDays(1), "x", "AntiCheat").join();

        final List<PunishmentAppliedEvent> applied = events(PunishmentAppliedEvent.class);
        assertEquals(1, applied.size());
        assertEquals(PunishmentType.TEMPBAN, applied.get(0).punishment().type());
        assertEquals("AntiCheat", applied.get(0).punishment().punisher());
        assertFalse(applied.get(0).silent());
    }

    @Test
    void unban_postsOneRevokedEventPerPunishment() {
        final TestServer server = start();
        server.command(server.console, "ban Steve one");
        server.command(server.console, "tempban Steve 1:d two");
        events.clear();

        server.command(server.console, "unban Steve appeal");

        final List<PunishmentRevokedEvent> revoked = events(PunishmentRevokedEvent.class);
        assertEquals(2, revoked.size(), events.toString());
        assertTrue(revoked.stream().allMatch(event -> event.punishment().status() == PunishmentStatus.REVOKED));
        assertTrue(revoked.stream().allMatch(event -> event.punishment().revokedReason().orElseThrow().equals("appeal")));
        assertTrue(revoked.stream().allMatch(event -> event.revokedBy().equals("Console")));
    }

    @Test
    void revokeAndRollback_postRevokedEvents() {
        final TestServer server = start();
        server.command(server.console, "mute Steve one");
        final long id = server.punishments.all().get(0).getId();
        events.clear();

        server.command(server.console, "revoke " + id + " mistake");
        final PunishmentRevokedEvent revoked = assertInstanceOf(PunishmentRevokedEvent.class, events.get(0));
        assertEquals(id, revoked.punishment().id());
        assertEquals(1, events.size());

        server.command(server.console, "tempban Alex 1:d griefing");
        server.command(server.console, "warn Alex toxic");
        events.clear();
        server.command(server.console, "staffrollback Console 1:h confirm");

        assertEquals(1, events(PunishmentRevokedEvent.class).size(), events.toString());
        assertEquals(1, events(WarnRevokedEvent.class).size(), events.toString());
    }

    @Test
    void edits_postThePunishmentBeforeAndAfter() {
        final TestServer server = start();
        server.command(server.console, "tempmute Steve 1:h spam");
        final long id = server.punishments.all().get(0).getId();
        events.clear();

        server.command(server.console, "extend " + id + " 1:h");
        server.command(server.console, "changereason " + id + " heavy spam");

        final List<PunishmentEditedEvent> edits = events(PunishmentEditedEvent.class);
        assertEquals(2, edits.size(), events.toString());
        final PunishmentEditedEvent extended = edits.get(0);
        assertTrue(extended.durationChanged());
        assertFalse(extended.reasonChanged());
        assertEquals(Duration.ofHours(1), Duration.between(extended.previous().endsAt().orElseThrow(), extended.current().endsAt().orElseThrow()));
        final PunishmentEditedEvent reason = edits.get(1);
        assertTrue(reason.reasonChanged());
        assertEquals("spam", reason.previous().reason());
        assertEquals("heavy spam", reason.current().reason());
        assertEquals("Console", reason.editedBy());
    }

    @Test
    void warnAndUnwarn_postWarnEvents() {
        final TestServer server = start();
        server.command(server.console, "warn Steve one");
        server.command(server.console, "warn Steve two");
        server.command(server.console, "warn Steve -s three");

        final List<WarnAppliedEvent> warns = events(WarnAppliedEvent.class);
        assertEquals(3, warns.size());
        assertTrue(warns.get(2).silent());
        assertEquals("three", warns.get(2).warn().reason());

        final long id = warns.get(0).warn().id();
        server.command(server.console, "unwarn " + id);
        final List<WarnRevokedEvent> removed = events(WarnRevokedEvent.class);
        assertEquals(1, removed.size());
        assertEquals(id, removed.get(0).warn().id());
        assertEquals("one", removed.get(0).warn().reason());
    }

    @Test
    void kick_postsAKickEvent() {
        final TestServer server = start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "kick Steve calm down");

        final PlayerKickedEvent kicked = assertInstanceOf(PlayerKickedEvent.class, events.get(0));
        assertEquals(1, events.size());
        assertEquals("Steve", kicked.targetName());
        assertEquals(steve.getUniqueId(), kicked.targetUniqueId());
        assertEquals("calm down", kicked.reason());
        assertTrue(kicked.logged().isEmpty(), "log-kicks is off");
    }

    @Test
    void failingListener_doesNotStopThePunishmentNorTheOtherListeners() {
        final TestServer server = start(TestServer.in(folder));
        final JudicatorApi api = server.judicator.getApi();
        api.getEventBus().subscribe(PunishmentAppliedEvent.class, event -> {
            throw new IllegalStateException("broken plugin");
        });
        api.getEventBus().subscribe(JudicatorEvent.class, events::add);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "mute Steve flood");
        assertEquals(1, server.punishments.all().size());
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(steve, "hello"));
        assertEquals(1, events.size());

        assertTrue(api.ban("Steve", "x", "AntiCheat").join().isSuccess(), "an API call is not failed by a listener");
        assertTrue(steve.wasKicked());
        assertEquals(2, events.size());
    }

    @Test
    void unsubscribedListener_receivesNothingMore() {
        final TestServer server = start(TestServer.in(folder));
        final Subscription subscription = server.judicator.getApi().getEventBus().subscribe(PunishmentAppliedEvent.class, events::add);

        server.command(server.console, "ban Steve one");
        subscription.unsubscribe();
        assertFalse(subscription.isActive());
        server.command(server.console, "ban Alex two");

        assertEquals(1, events.size());
    }

    @Test
    void syncedChange_isPostedOnlyWhereItWasMade() {
        final TestServer a = start(TestServer.in(folder.resolve("a")).set("sync.enabled", true));
        final TestServer b = start(TestServer.in(folder.resolve("b")).set("sync.enabled", true).sharingDatabaseWith(a));
        final List<JudicatorEvent> onA = new ArrayList<>();
        final List<JudicatorEvent> onB = new ArrayList<>();
        a.judicator.getApi().getEventBus().subscribe(JudicatorEvent.class, onA::add);
        b.judicator.getApi().getEventBus().subscribe(JudicatorEvent.class, onB::add);
        final FakePlayer steve = b.online(new FakePlayer("Steve", "10.0.0.1"));

        a.command(a.console, "ban Steve hacking");
        b.pollSync();

        assertTrue(steve.wasKicked(), "B applied it to its player");
        assertEquals(1, onA.size());
        assertTrue(onB.isEmpty(), onB.toString());
    }

}
