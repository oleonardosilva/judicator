package com.elless.judicator.features;

import com.elless.judicator.jails.Jail;
import com.elless.judicator.platform.Location;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlatform;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jails on one game server: made in game with /jails, filled with /jail and /tempjail, held by the checks of where each
 * prisoner is, with their secret escape routes, fugitives and guards.
 */
class JailTest {

    /** the jail's spawn; its default area is 3 blocks around */
    private static final Location CELL = new Location("world", 100.5, 64, 100.5, 0, 0);

    @TempDir
    Path folder;

    private TestServer server;
    private FakePlayer admin;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private TestServer start(TestServer.Builder builder) {
        server = builder.platform(PlatformType.BUKKIT).start();
        admin = server.online(new FakePlayer("Warden", "10.0.0.9", "judicator.admin", "judicator.notify", "judicator.jail.notify"));
        return server;
    }

    private void makeJail(String name, Location at) {
        server.platform.place(admin, at);
        server.command(admin, "jails create " + name);
    }

    private Punishment only(String name) {
        final List<Punishment> all = server.punishments.findAllByUsername(name);
        assertEquals(1, all.size(), all.toString());
        return all.get(0);
    }

    private void walk(FakePlayer player, double x, double y, double z) {
        server.platform.place(player, x, y, z);
        server.checkPrisoners();
    }

    @Test
    void jail_takesThePlayerToTheJailAndKeepsThemThere() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        assertTrue(admin.received("Jail cell created here"), admin.messages().toString());
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.2"));

        server.command(server.console, "jail Steve griefing");

        final Punishment jail = only("Steve");
        assertEquals(PunishType.JAIL, jail.getType());
        assertEquals("cell", jail.getJail().orElseThrow());
        assertEquals(List.of("Steve -> 100 64 100"), server.platform.placeTeleports());
        assertTrue(steve.received("You have been sent to jail."), steve.messages().toString());
        assertTrue(steve.received("Jail: cell"));
        assertTrue(other.received("User Steve was jailed by Console."), other.messages().toString());
        assertTrue(admin.received("[Staff] Console applied JAIL to Steve: griefing"), admin.messages().toString());

        // moving inside the cell is fine
        walk(steve, 102, 64, 101);
        assertEquals(1, server.platform.placeTeleports().size());
    }

    @Test
    void walkingOutOfTheArea_isCaught_andATempJailGetsThePenalty() {
        start(TestServer.in(folder).set("jail.escape.caught-penalty", "10:m"));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempjail Steve 1:h griefing");
        final LocalDateTime end = only("Steve").getFinishAt().orElseThrow();

        walk(steve, 103.5, 64, 100.5);
        walk(steve, 104.5, 64, 100.5);

        assertTrue(steve.received("The guards caught you leaving the jail."), steve.messages().toString());
        assertTrue(steve.received("+10 minutes in jail"), steve.messages().toString());
        assertEquals(CELL, server.platform.where(steve));
        assertEquals(end.plusMinutes(10).withNano(0), only("Steve").getFinishAt().orElseThrow().withNano(0));
    }

    @Test
    void teleportedAway_isBroughtBackWithoutPenalty() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempjail Steve 1:h griefing");
        final LocalDateTime end = only("Steve").getFinishAt().orElseThrow();

        walk(steve, 500, 70, -300);

        assertTrue(steve.received("You cannot leave the jail."), steve.messages().toString());
        assertEquals(CELL, server.platform.where(steve));
        assertEquals(end, only("Steve").getFinishAt().orElseThrow());
    }

    @Test
    void secretEscapeRoute_makesAFugitive_andAGuardTakesThemBack() {
        start(TestServer.in(folder).set("jail.escape.caught-penalty", "5:m").set("jail.escape.fugitive-time", "10:m"));
        makeJail("cell", CELL);
        server.platform.place(admin, new Location("world", 102.5, 64, 102.5, 0, 0));
        server.command(admin, "jails escape cell add 100 1");
        assertTrue(admin.received("Secret escape route added to cell"), admin.messages().toString());
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer guard = server.online(new FakePlayer("Guard", "10.0.0.3", "judicator.jail.guard"));
        final FakePlayer someone = server.online(new FakePlayer("Someone", "10.0.0.4"));
        server.command(server.console, "tempjail Steve 1:h griefing");

        walk(steve, 102.5, 64, 102.5);

        assertTrue(steve.received("You escaped! The guards will hunt you for 10 minutes."), steve.messages().toString());
        assertTrue(admin.received("[Jail] Steve escaped from cell"), admin.messages().toString());
        final Punishment escaped = only("Steve");
        assertTrue(escaped.isEscaped());
        assertNotNull(escaped.getJailLeft());
        // free to go, and to run commands
        walk(steve, 300, 64, 300);
        assertEquals(new Location("world", 300, 64, 300, 0, 0), server.platform.where(steve));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/spawn"));
        assertEquals(0, server.judicator.getJailService().occupants(server.judicator.getJailService().find("cell").orElseThrow()));

        assertTrue(server.hit(someone, steve), "a player who is not a guard just hits");
        assertFalse(server.hit(guard, steve), "the guard's hit catches instead of hurting");

        assertTrue(steve.received("You were caught by Guard and taken back to jail."), steve.messages().toString());
        assertTrue(guard.received("You caught Steve: back to jail."));
        assertTrue(admin.received("[Jail] Steve was caught by Guard and taken back to cell"), admin.messages().toString());
        assertEquals(CELL, server.platform.where(steve));
        final Punishment caught = only("Steve");
        assertFalse(caught.isEscaped());
        final long left = Duration.between(LocalDateTime.now(), caught.getFinishAt().orElseThrow()).toMinutes();
        assertTrue(left >= 63 && left <= 65, "about an hour and the penalty left: " + left);
    }

    @Test
    void escapeRouteWithoutLuck_catchesThePrisoner() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.platform.place(admin, new Location("world", 102.5, 64, 102.5, 0, 0));
        server.command(admin, "jails escape cell add 0");
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");

        walk(steve, 102.5, 64, 102.5);

        assertTrue(steve.received("The guards caught you escaping!"), steve.messages().toString());
        assertEquals(CELL, server.platform.where(steve));
        assertFalse(only("Steve").isEscaped());
    }

    @Test
    void withoutFugitiveTime_anEscapeFreesAtOnce() {
        start(TestServer.in(folder).set("jail.escape.fugitive-time", "0"));
        makeJail("cell", CELL);
        server.platform.place(admin, new Location("world", 102.5, 64, 102.5, 0, 0));
        server.command(admin, "jails escape cell add");
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");

        walk(steve, 102.5, 64, 102.5);
        server.checkPrisoners();

        assertTrue(steve.received("You escaped and are free!"), steve.messages().toString());
        assertTrue(server.judicator.getPunishService().findAllActiveOf(server.judicator.getPlayerDirectory().resolve("Steve"),
                PunishType.JAIL).isEmpty(), "the jail is over");
        assertFalse(steve.received("The guards stopped looking for you"));
    }

    @Test
    void aJailNotShared_holdsOnePrisoner_andAnotherJailTakesTheNext() {
        start(TestServer.in(folder));
        makeJail("solitary", CELL);
        server.command(admin, "jails shared solitary false");
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.2"));

        server.command(server.console, "jail Steve -jail=solitary griefing");
        server.command(admin, "jail Alex -jail=solitary griefing");
        assertTrue(admin.received("The jail 'solitary' is full."), admin.messages().toString());
        server.command(admin, "jail Alex griefing");
        assertTrue(admin.received("Every jail is full."), admin.messages().toString());

        makeJail("block-b", new Location("world", 200.5, 64, 200.5, 0, 0));
        server.command(server.console, "jail Alex griefing");
        assertEquals("block-b", only("Alex").getJail().orElseThrow());
    }

    @Test
    void capacity_limitsASharedJail() {
        start(TestServer.in(folder));
        makeJail("yard", CELL);
        server.command(admin, "jails capacity yard 2");
        assertTrue(admin.received("Jail yard holds 2 prisoner(s)."), admin.messages().toString());
        for (String name : List.of("A1", "B2", "C3")) {
            server.online(new FakePlayer(name, "10.0.1." + name.charAt(1)));
            server.command(server.console, "jail " + name + " griefing");
        }
        assertEquals(1, server.punishments.findAllByUsername("A1").size());
        assertEquals(1, server.punishments.findAllByUsername("B2").size());
        assertTrue(server.punishments.findAllByUsername("C3").isEmpty());
    }

    @Test
    void withoutJails_jailIsRefused() {
        start(TestServer.in(folder));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(admin, "jail Steve griefing");
        assertTrue(admin.received("There are no jails yet"), admin.messages().toString());
        assertTrue(server.punishments.findAllByUsername("Steve").isEmpty());
    }

    @Test
    void aPrisonerCannotBeJailedTwice() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");
        server.command(admin, "jail Steve again");
        assertTrue(admin.received("Steve is already in jail."), admin.messages().toString());
        assertEquals(1, server.punishments.findAllByUsername("Steve").size());
    }

    @Test
    void prisoners_onlyRunTheAllowedCommands_andCannotBuildNorFight() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.2"));
        server.command(server.console, "jail Steve griefing");

        assertEquals(Outcome.BLOCKED_MUTED, server.playerCommand(steve, "/spawn"));
        assertTrue(steve.received("Prisoners cannot use this command."));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/msg Other help"));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/minecraft:tell Other help"));
        assertFalse(server.judicator.getJailService().mayBreak(steve));
        assertFalse(server.judicator.getJailService().mayPlace(steve));
        assertFalse(server.hit(steve, other), "prisoners do not hit");
        assertFalse(server.hit(other, steve), "prisoners are not hit");
        assertTrue(server.judicator.getJailService().mayBreak(other));
        assertEquals(Outcome.ALLOWED, server.playerCommand(other, "/spawn"));
    }

    @Test
    void unjail_letsThePrisonerOutToTheReleasePoint() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.platform.place(admin, new Location("world", 50.5, 70, 50.5, 0, 0));
        server.command(admin, "jails release cell");
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");

        server.command(admin, "unjail Steve good behaviour");

        assertTrue(admin.received("Released Steve from 1 jail sentence(s)."), admin.messages().toString());
        assertTrue(steve.received("Your punishment has been revoked!"), steve.messages().toString());
        assertEquals(new Location("world", 50.5, 70, 50.5, 0, 0), server.platform.where(steve));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/spawn"));
        server.command(admin, "unjail Steve");
        assertTrue(admin.received("This player is not in jail."));
    }

    @Test
    void aTempJailThatEnds_letsThePrisonerOutToTheWorldSpawn() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempjail Steve 1:h griefing");

        server.judicator.getPunishService().changeEnd(only("Steve"), LocalDateTime.now().minusSeconds(1), null);
        server.checkPrisoners();

        assertTrue(steve.received("You served your time and are free."), steve.messages().toString());
        assertEquals(FakePlatform.WORLD_SPAWN, server.platform.where(steve));
    }

    @Test
    void timeOnlyRunsOnline_andARejoiningPrisonerIsTakenBack() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempjail Steve 1:h griefing");
        assertTrue(only("Steve").isOnlineOnly(), "jail.online-only is on by default");

        server.quit(steve);
        assertTrue(only("Steve").isPaused());
        server.platform.place(steve, 0, 64, 0);
        server.online(steve);

        assertFalse(only("Steve").isPaused());
        assertEquals(CELL, server.platform.where(steve));
    }

    @Test
    void jailedWhileOffline_theTimeStartsAtTheirLogin() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = new FakePlayer("Steve", "10.0.0.1");
        server.online(steve);
        server.quit(steve);

        server.command(server.console, "tempjail Steve 1:h griefing");
        assertTrue(only("Steve").isPaused());

        server.online(steve);
        assertEquals(CELL, server.platform.where(steve));
    }

    @Test
    void someoneLeftInAJailThatEnded_isLetOutAtLogin() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");
        server.quit(steve);
        server.command(server.console, "unjail Steve");

        server.online(steve);

        assertEquals(FakePlatform.WORLD_SPAWN, server.platform.where(steve));
    }

    @Test
    void jails_areMadeAndChangedInGame() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);

        server.platform.place(admin, 95, 60, 95);
        server.command(admin, "jails pos1 cell");
        server.platform.place(admin, 110, 70, 110);
        server.command(admin, "jails pos2 cell");
        server.platform.place(admin, 300, 64, 300);
        server.command(admin, "jails spawn cell");
        assertTrue(admin.received("The spawn of the jail 'cell' must be inside its area"), admin.messages().toString());
        server.command(admin, "jails server cell prison");
        server.command(admin, "jails info cell");

        final Jail jail = server.judicator.getJailService().find("cell").orElseThrow();
        assertEquals("95 60 95 -> 110 70 110", jail.area().describe());
        assertEquals("prison", jail.server());
        assertTrue(admin.received("Area: 95 60 95 -> 110 70 110"), admin.messages().toString());

        server.command(admin, "jails create Cell");
        assertTrue(admin.received("There is already a jail called 'cell'."), admin.messages().toString());
        server.command(admin, "jails delete cell");
        assertTrue(server.judicator.getJailService().all().isEmpty());
        assertTrue(server.jails.findAll().isEmpty());
    }

    @Test
    void aReasonOfPunishCanJail() {
        start(TestServer.in(folder).set("reasons.griefing", java.util.Map.of("reason", "Griefing", "type", "TEMPJAIL", "duration", "1:h", "ip", true)));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "punish Steve Griefing");

        final Punishment jail = only("Steve");
        assertEquals(PunishType.TEMPJAIL, jail.getType());
        assertEquals("cell", jail.getJail().orElseThrow());
        assertTrue(jail.getIpAddress().isEmpty(), "a jail is for the account");
        assertTrue(jail.isOnlineOnly());
        assertEquals(CELL, server.platform.where(steve));
    }

    @Test
    void aReasonCanNameItsJail() {
        start(TestServer.in(folder).set("reasons.griefing", java.util.Map.of("reason", "Griefing", "steps", List.of(
                java.util.Map.of("type", "TEMPMUTE", "duration", "1:h"),
                java.util.Map.of("type", "TEMPJAIL", "duration", "1:h", "jail", "solitary")))));
        makeJail("cell", CELL);
        makeJail("solitary", new Location("world", 200.5, 64, 200.5, 0, 0));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "punish Steve Griefing");
        server.command(server.console, "punish Steve Griefing");

        final Punishment jail = server.punishments.findAllByUsername("Steve").stream()
                .filter(Punishment::isJailType).findFirst().orElseThrow();
        assertEquals("solitary", jail.getJail().orElseThrow(), "the second step jails, in its own jail");
        assertEquals(new Location("world", 200.5, 64, 200.5, 0, 0), server.platform.where(steve));
    }

    @Test
    void anEscapeRouteCannotReachTheSpawn() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.platform.place(admin, new Location("world", 101.5, 64, 100.5, 0, 0));
        server.command(admin, "jails escape cell add 50 2");
        assertTrue(admin.received("An escape route of the jail 'cell' cannot reach its spawn"), admin.messages().toString());
        assertTrue(server.judicator.getJailService().find("cell").orElseThrow().escapes().isEmpty());

        server.platform.place(admin, new Location("world", 103.5, 64, 103.5, 0, 0));
        server.command(admin, "jails escape cell add 50 1");
        server.command(admin, "jails spawn cell");
        assertTrue(admin.received("must be inside its area and away from its escape routes"), admin.messages().toString());
        assertEquals(CELL, server.judicator.getJailService().find("cell").orElseThrow().spawn());
    }

    @Test
    void aJailConfirmedAfterTheTargetWasJailed_isRefused() {
        start(TestServer.in(folder));
        makeJail("solitary", CELL);
        server.command(admin, "jails shared solitary false");
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.2"));

        server.command(admin, "jail Alex -jail=solitary griefing");
        server.command(server.console, "jail Steve -jail=solitary griefing");
        server.chat(admin, "confirm");

        assertTrue(admin.received("The jail 'solitary' is full."), admin.messages().toString());
        assertTrue(server.punishments.findAllByUsername("Alex").isEmpty());
    }

    @Test
    void aPluginReload_keepsThePrisonersOnline() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");

        server.reloadPlugin();
        walk(steve, 500, 70, -300);

        assertEquals(CELL, server.platform.where(steve));
        assertEquals(Outcome.BLOCKED_MUTED, server.playerCommand(steve, "/spawn"));
    }

    @Test
    void clearingThePlayersData_letsThemOut() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");

        server.command(server.console, "judicator clear Steve confirm");

        assertEquals(FakePlatform.WORLD_SPAWN, server.platform.where(steve));
        assertEquals(Outcome.ALLOWED, server.playerCommand(steve, "/spawn"));
    }

    @Test
    void aJailWithAFugitive_cannotBeDeleted() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.platform.place(admin, new Location("world", 102.5, 64, 102.5, 0, 0));
        server.command(admin, "jails escape cell add");
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "jail Steve griefing");
        walk(steve, 102.5, 64, 102.5);
        assertTrue(only("Steve").isEscaped());

        server.command(admin, "jails delete cell");

        assertTrue(admin.received("The jail 'cell' has prisoners"), admin.messages().toString());
        assertTrue(server.judicator.getJailService().find("cell").isPresent());
    }

    @Test
    void theJailsSurviveARestart() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.restart();
        assertEquals(CELL, server.judicator.getJailService().find("cell").orElseThrow().spawn());
    }

    @Test
    void aJailCanBeExtendedAndListed() {
        start(TestServer.in(folder));
        makeJail("cell", CELL);
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempjail Steve 1:h griefing");
        final Punishment jail = only("Steve");

        server.command(admin, "extend " + jail.getId() + " 1:h");
        server.command(admin, "jaillist");

        assertTrue(only("Steve").getFinishAt().orElseThrow().isAfter(jail.getFinishAt().orElseThrow().plusMinutes(59)));
        assertTrue(admin.received("Prisoners (1 in total, page 1/1)"), admin.messages().toString());
        assertTrue(admin.received("Steve - cell"), admin.messages().toString());
    }

    @Test
    void theProxyHasNoJailsCommand_soItGoesThroughToTheGameServer() {
        server = TestServer.in(folder).start();
        assertTrue(server.judicator.getCommands().stream().noneMatch(command -> command.getName().equals("jails")));
        assertTrue(server.judicator.getCommands().stream().anyMatch(command -> command.getName().equals("jail")));
    }

    @Test
    void theConsoleCannotSetAPlace() {
        start(TestServer.in(folder));
        server.command(server.console, "jails create cell");
        assertTrue(server.console.received("Only a player on a game server can do this"), String.valueOf(server.console));
        assertNull(server.judicator.getJailService().find("cell").orElse(null));
    }
}
