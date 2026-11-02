package com.elless.judicator.features;

import com.elless.judicator.platform.Location;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jails in a network: Velocity in front of a lobby and of a "prison" game server running Judicator in proxy-mode, all on
 * one database with sync. The proxy sends prisoners to the prison and keeps them there; the prison holds them.
 */
class JailNetworkTest {

    private static final Location CELL = new Location("world", 10.5, 64, 10.5, 0, 0);

    @TempDir
    Path folder;

    private final List<TestServer> servers = new ArrayList<>();
    private TestServer proxy;
    private TestServer prison;

    @BeforeEach
    void start() {
        proxy = track(TestServer.in(folder.resolve("proxy")).set("sync.enabled", true).start());
        proxy.platform.servers(List.of("lobby", "prison"), List.of("lobby"));
        prison = track(TestServer.in(folder.resolve("prison")).platform(PlatformType.BUKKIT)
                .set("sync.enabled", true).set("proxy-mode", true).set("scopes.server-name", "prison")
                .sharingDatabaseWith(proxy).start());
        final FakePlayer warden = new FakePlayer("Warden", "10.0.0.9", "judicator.admin", "judicator.jail.notify");
        proxy.online(warden);
        proxy.platform.moveTo(warden, "prison");
        prison.online(warden);
        prison.platform.place(warden, CELL);
        prison.command(warden, "jails create cell");
        proxy.pollSync();
    }

    private TestServer track(TestServer server) {
        servers.add(server);
        return server;
    }

    @AfterEach
    void stop() {
        servers.forEach(TestServer::close);
    }

    @Test
    void aJailMadeOnTheGameServer_isOnThatServer_andTheProxyKnowsIt() {
        assertEquals("prison", proxy.judicator.getJailService().find("cell").orElseThrow().server());
    }

    @Test
    void jailOnTheProxy_sendsThePrisonerToThePrison_whichHoldsThem() {
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        proxy.online(steve);
        assertEquals(Optional.of("lobby"), proxy.chooseInitialServer(steve, "lobby"));

        proxy.command(proxy.console, "jail Steve griefing");

        assertEquals(Optional.of("prison"), proxy.platform.serverOf(steve));
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("You have been sent to jail.")).count());
        // the player arrives at the prison
        prison.online(steve);
        assertEquals(CELL, prison.platform.where(steve));

        assertFalse(proxy.switchServer(steve, "lobby"));
        assertTrue(steve.received("You are in jail: you cannot go to another server."), steve.messages().toString());
        proxy.quit(steve);
        proxy.online(steve);
        assertEquals(Optional.of("prison"), proxy.chooseInitialServer(steve, "lobby"));
    }

    @Test
    void aPrisonerAlreadyOnThePrison_isTakenToTheCellThroughSync() {
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        proxy.online(steve);
        proxy.platform.moveTo(steve, "prison");
        prison.online(steve);

        proxy.command(proxy.console, "jail Steve griefing");
        prison.pollSync();

        assertEquals(CELL, prison.platform.where(steve));
        assertEquals(1, steve.messages().stream().filter(m -> m.contains("You have been sent to jail.")).count(),
                "only the proxy tells: " + steve.messages());
    }

    @Test
    void anEscapeOnThePrison_letsTheFugitiveLeave_andTheProxyTellsTheStaff() {
        final FakePlayer warden = (FakePlayer) prison.platform.getPlayer("Warden").orElseThrow();
        prison.platform.place(warden, new Location("world", 12.5, 64, 12.5, 0, 0));
        prison.command(warden, "jails escape cell add");
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        proxy.online(steve);
        proxy.command(proxy.console, "jail Steve griefing");
        prison.online(steve);

        prison.platform.place(steve, 12.5, 64, 12.5);
        prison.checkPrisoners();
        proxy.pollSync();

        assertTrue(steve.received("You escaped!"), steve.messages().toString());
        assertEquals(1, warden.messages().stream().filter(m -> m.contains("[Jail] Steve escaped from cell")).count(),
                "the proxy tells, once: " + warden.messages());
        assertTrue(proxy.switchServer(steve, "lobby"), "a fugitive can leave the prison");

        // caught on the lobby... which has no Judicator here: a guard on the prison catches them instead
        prison.platform.place(steve, 40, 64, 40);
        final FakePlayer guard = new FakePlayer("Guard", "10.0.0.5", "judicator.jail.guard");
        proxy.online(guard);
        prison.online(guard);
        proxy.platform.moveTo(steve, "prison");
        assertFalse(prison.hit(guard, steve));
        proxy.pollSync();

        final Punishment jail = proxy.punishments.findAllByUsername("Steve").get(0);
        assertFalse(jail.isEscaped());
        assertEquals(CELL, prison.platform.where(steve));
        assertFalse(proxy.switchServer(steve, "lobby"));
    }

    @Test
    void theProxyLimitsTheCommandsOfItsOwnToo() {
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        proxy.online(steve);
        assertEquals(TestServer.Outcome.ALLOWED, proxy.playerCommand(steve, "party invite Alex"));

        proxy.command(proxy.console, "jail Steve griefing");

        assertEquals(TestServer.Outcome.BLOCKED_MUTED, proxy.playerCommand(steve, "party invite Alex"));
        assertTrue(steve.received("Prisoners cannot use this command."), steve.messages().toString());
        assertEquals(TestServer.Outcome.ALLOWED, proxy.playerCommand(steve, "msg Alex help"));

        // known again after a reconnection
        proxy.quit(steve);
        proxy.online(steve);
        assertEquals(TestServer.Outcome.BLOCKED_MUTED, proxy.playerCommand(steve, "party invite Alex"));

        proxy.command(proxy.console, "unjail Steve");
        assertEquals(TestServer.Outcome.ALLOWED, proxy.playerCommand(steve, "party invite Alex"));
    }

    @Test
    void unjailOnTheProxy_freesThePrisonerOnThePrison() {
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        proxy.online(steve);
        proxy.command(proxy.console, "jail Steve griefing");
        prison.online(steve);

        proxy.command(proxy.console, "unjail Steve");
        prison.pollSync();

        assertTrue(proxy.switchServer(steve, "lobby"));
        assertFalse(prison.judicator.getJailService().isPrisonerHere(steve));
    }
}
