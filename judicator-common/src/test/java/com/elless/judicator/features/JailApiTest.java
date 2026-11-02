package com.elless.judicator.features;

import com.elless.judicator.api.ActionResult;
import com.elless.judicator.api.ActionStatus;
import com.elless.judicator.api.JudicatorApi;
import com.elless.judicator.api.event.JudicatorEvent;
import com.elless.judicator.api.event.PrisonerCaughtEvent;
import com.elless.judicator.api.event.PrisonerEscapedEvent;
import com.elless.judicator.api.event.PunishmentAppliedEvent;
import com.elless.judicator.api.jail.JailView;
import com.elless.judicator.api.punishment.PunishmentRequest;
import com.elless.judicator.api.punishment.PunishmentType;
import com.elless.judicator.api.punishment.PunishmentView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jails through judicator-api: applying, looking up, the jails themselves and the events of escapes and catches.
 */
class JailApiTest {

    private static final Location CELL = new Location("world", 100.5, 64, 100.5, 0, 0);

    @TempDir
    Path folder;

    private TestServer server;
    private JudicatorApi api;
    private FakePlayer warden;
    private final List<JudicatorEvent> events = new ArrayList<>();

    @BeforeEach
    void start() {
        server = TestServer.in(folder).platform(PlatformType.BUKKIT).start();
        api = server.judicator.getApi();
        api.getEventBus().subscribe(JudicatorEvent.class, events::add);
        warden = server.online(new FakePlayer("Warden", "10.0.0.9", "judicator.admin"));
        server.platform.place(warden, CELL);
        server.command(warden, "jails create cell");
        server.command(warden, "jails capacity cell 2");
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private <T extends JudicatorEvent> List<T> events(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void tempjail_isAppliedAndLookedUp() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        final ActionResult<PunishmentView> result = api.tempjail("Steve", Duration.ofHours(1), "griefing", "AntiGrief").join();

        assertEquals(ActionStatus.SUCCESS, result.status());
        final PunishmentView jail = result.value().orElseThrow();
        assertEquals(PunishmentType.TEMPJAIL, jail.type());
        assertEquals(Optional.of("cell"), jail.jail());
        assertTrue(jail.onlineOnly(), "jail.online-only");
        assertEquals(CELL, server.platform.where(steve));
        assertTrue(api.isJailed("Steve").join());
        assertEquals(jail.id(), api.getActiveJail(steve.getUniqueId()).join().orElseThrow().id());
        assertEquals(1, events(PunishmentAppliedEvent.class).size());

        assertEquals(ActionStatus.ALREADY_JAILED, api.jail("Steve", "again", "AntiGrief").join().status());
        assertEquals(ActionStatus.SUCCESS, api.unjail("Steve", null, "AntiGrief").join().status());
        assertFalse(api.isJailed("Steve").join());
    }

    @Test
    void aJailThatDoesNotExistOrIsFull_isRefused() {
        server.online(new FakePlayer("A1", "10.0.0.1"));
        server.online(new FakePlayer("B2", "10.0.0.2"));
        server.online(new FakePlayer("C3", "10.0.0.3"));

        assertEquals(ActionStatus.NO_JAIL, api.punish(PunishmentRequest.builder(PunishmentType.JAIL, "A1").jail("nowhere").build()).join().status());
        api.jail("A1", null, "Plugin").join();
        api.jail("B2", null, "Plugin").join();
        assertEquals(ActionStatus.NO_JAIL, api.jail("C3", null, "Plugin").join().status());

        final JailView cell = api.getJails().join().get(0);
        assertEquals("cell", cell.name());
        assertEquals(2, cell.prisoners());
        assertEquals(2, cell.room());
        assertFalse(cell.hasRoom());
    }

    @Test
    void escapesAndCatchesPostEvents() {
        server.platform.place(warden, new Location("world", 102.5, 64, 102.5, 0, 0));
        server.command(warden, "jails escape cell add");
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer guard = server.online(new FakePlayer("Guard", "10.0.0.2", "judicator.jail.guard"));
        api.jail("Steve", "griefing", "Plugin").join();

        server.platform.place(steve, 102.5, 64, 102.5);
        server.checkPrisoners();

        assertEquals(1, events(PrisonerEscapedEvent.class).size());
        final PunishmentView fugitive = api.getActiveJail("Steve").join().orElseThrow();
        assertTrue(fugitive.isFugitive());
        assertTrue(fugitive.escapedAt().isPresent());
        assertFalse(api.isJailed("Steve").join(), "a fugitive walks free");

        server.hit(guard, steve);

        final PrisonerCaughtEvent caught = events(PrisonerCaughtEvent.class).get(0);
        assertEquals("Guard", caught.guard());
        assertFalse(caught.punishment().isFugitive());
        assertTrue(api.isJailed("Steve").join());
    }
}
