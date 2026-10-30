package com.elless.judicator.features;

import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.punish.PunishmentBuilder;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
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
 * Punishments for one server or a group of servers ({@code -scope=}, {@code scopes} in config.yml). The proxy has
 * lobby, survival1, survival2 and creative; the group "survival" is survival1 and survival2; the try list is lobby,
 * creative.
 */
class ScopeTest {

    @TempDir
    Path folder;

    private final List<TestServer> servers = new ArrayList<>();

    @AfterEach
    void stop() {
        servers.forEach(TestServer::close);
    }

    private TestServer proxy() {
        return proxy(TestServer.in(folder));
    }

    private TestServer proxy(TestServer.Builder builder) {
        final TestServer server = builder.set("scopes.groups.survival", List.of("survival1", "Survival2")).start();
        server.platform.servers(List.of("lobby", "survival1", "survival2", "creative"), List.of("lobby", "creative"));
        servers.add(server);
        return server;
    }

    private static FakePlayer on(TestServer server, String name, String where) {
        final FakePlayer player = server.online(new FakePlayer(name, "10.0.0." + (name.length() + 1)));
        server.platform.moveTo(player, where);
        return player;
    }

    @Test
    void aBanForAGroup_keepsThePlayerInTheNetworkButOutOfItsServers() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "lobby");

        proxy.command(proxy.console, "ban Steve -scope=survival hacking");

        assertEquals(0, steve.kicks());
        assertFalse(proxy.switchServer(steve, "survival2"));
        assertTrue(steve.received("You are banned from survival: you cannot join it."), steve.messages().toString());
        assertTrue(proxy.switchServer(steve, "creative"));
        proxy.quit(steve);
        assertTrue(proxy.join(new FakePlayer("Steve", "10.0.0.6")).isEmpty(), "a ban for some servers does not refuse the network");
    }

    @Test
    void aBanForTheServerThePlayerIsOn_sendsThemToTheFirstServerOfTheTryListLeft() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "lobby");

        proxy.command(proxy.console, "ban Steve -scope=lobby griefing");

        assertEquals(0, steve.kicks());
        assertEquals(Optional.of("creative"), proxy.platform.serverOf(steve));
        assertTrue(steve.received("You are banned from lobby: you cannot join it."), steve.messages().toString());
    }

    @Test
    void aBanForAServerAppliedWhileThePlayerIsOnTheirWayThere_movesThemOnArrival() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "lobby");

        proxy.switchServer(steve, "survival1", () -> proxy.command(proxy.console, "ban Steve -scope=survival x"));

        assertEquals(Optional.of("lobby"), proxy.platform.serverOf(steve));
        assertEquals(0, steve.kicks());
    }

    @Test
    void withNoServerOfTheTryListLeft_thePlayerIsDisconnected() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "survival1");
        proxy.command(proxy.console, "ban Steve -scope=lobby a");
        proxy.command(proxy.console, "ban Steve -scope=creative b");
        proxy.platform.moveTo(steve, "survival1");

        proxy.command(proxy.console, "ban Steve -scope=survival c");

        assertEquals(1, steve.kicks());
    }

    @Test
    void aServerThatRefusesTheConnection_disconnectsInstead() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "survival1");
        proxy.platform.refuseConnections("lobby");
        proxy.platform.refuseConnections("creative");

        proxy.command(proxy.console, "ban Steve -scope=survival1 x");

        assertEquals(1, steve.kicks());
    }

    @Test
    void atLogin_aServerTheBanCovers_isReplacedByTheNextOneOfTheTryList() {
        final TestServer proxy = proxy();
        proxy.quit(on(proxy, "Steve", "lobby"));
        proxy.command(proxy.console, "ban Steve -scope=lobby x");

        final FakePlayer steve = proxy.online(new FakePlayer("Steve", "10.0.0.6"));

        assertEquals(Optional.of("creative"), proxy.chooseInitialServer(steve, "lobby"));
        proxy.command(proxy.console, "ban Steve -scope=creative y");
        final FakePlayer again = proxy.online(new FakePlayer("Steve", "10.0.0.6"));
        assertEquals(Optional.empty(), proxy.chooseInitialServer(again, "lobby"));
        assertTrue(again.kickMessage().contains("You have been banned"), again.kickMessage());
    }

    @Test
    void aMuteForAGroup_onlySilencesThere_andAMuteForEverywhereStillHoldsElsewhere() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "lobby");
        proxy.command(proxy.console, "mute Steve -scope=survival spam");

        assertEquals(Outcome.ALLOWED, proxy.chat(steve, "hi lobby"));
        assertTrue(proxy.switchServer(steve, "survival1"));
        assertEquals(Outcome.BLOCKED_MUTED, proxy.chat(steve, "hi survival"));

        proxy.command(proxy.console, "softmute Steve flood");
        assertEquals(Outcome.BLOCKED_MUTED, proxy.chat(steve, "still survival"), "the normal mute weighs more there");
        assertTrue(proxy.switchServer(steve, "lobby"));
        assertEquals(Outcome.SOFT_MUTED, proxy.chat(steve, "lobby again"));
    }

    @Test
    void aMuteForAGroup_isCachedAtLoginForEveryServer() {
        final TestServer proxy = proxy();
        proxy.quit(on(proxy, "Steve", "lobby"));
        proxy.command(proxy.console, "mute Steve -scope=survival spam");

        final FakePlayer steve = on(proxy, "Steve", "survival2");

        assertEquals(Outcome.BLOCKED_MUTED, proxy.chat(steve, "hi"));
    }

    @Test
    void aPunishmentForOtherServers_isNotAnnouncedWhereThePlayerIs() {
        final TestServer proxy = proxy();
        on(proxy, "Steve", "lobby");

        proxy.command(proxy.console, "mute Steve -scope=survival spam");
        assertTrue(proxy.platform.announcements().isEmpty(), proxy.platform.announcements().toString());

        proxy.command(proxy.console, "mute Steve flood");
        assertFalse(proxy.platform.announcements().isEmpty());
    }

    @Test
    void aKickHasNoScope() {
        final Punishment kick = new PunishmentBuilder().type(PunishType.KICK).reason("x").target("Steve", null)
                .scope("server:survival1").build();

        assertTrue(kick.getScope().isEmpty(), "a kick is over at once: it applies on no particular server");
    }

    @Test
    void anUnknownScope_isRefused() {
        final TestServer proxy = proxy();
        on(proxy, "Steve", "lobby");

        proxy.command(proxy.console, "ban Steve -scope=nowhere x");

        assertTrue(proxy.console.received("Unknown scope 'nowhere'"), proxy.console.messages().toString());
        assertTrue(proxy.punishments.all().isEmpty());
    }

    @Test
    void theScopeIsStoredShownAndOfferedInTabCompletion() {
        final TestServer proxy = proxy();
        on(proxy, "Steve", "lobby");

        proxy.command(proxy.console, "tempban Steve 1:d -scope=SURVIVAL1 -s x");

        final Punishment ban = proxy.punishments.all().get(0);
        assertEquals("server:survival1", ban.getScope().orElseThrow());
        proxy.command(proxy.console, "pview " + ban.getId());
        assertTrue(proxy.console.received("Scope: survival1"), proxy.console.messages().toString());
        assertEquals(List.of("-scope=survival", "-scope=survival1", "-scope=survival2"), proxy.suggest(proxy.console, "ban Steve -scope=su"));
    }

    @Test
    void punishWithAReason_takesTheScopeToo() {
        final TestServer proxy = proxy();
        on(proxy, "Steve", "lobby");
        final String reason = proxy.judicator.getReasonCache().getReasons().get(0).getReason();

        proxy.command(proxy.console, "punish Steve -scope=creative " + reason);

        assertEquals("server:creative", proxy.punishments.all().get(0).getScope().orElseThrow());
    }

    @Test
    void unban_liftsTheBansForSomeServersToo() {
        final TestServer proxy = proxy();
        final FakePlayer steve = on(proxy, "Steve", "lobby");
        proxy.command(proxy.console, "ban Steve -scope=survival x");

        proxy.command(proxy.console, "unban Steve");

        assertTrue(proxy.punishments.all().get(0).isRevoked());
        assertTrue(proxy.switchServer(steve, "survival1"));
    }

    @Test
    void altsOfAPlayerBannedFromSomeServers_canStillJoin() {
        final TestServer proxy = proxy(TestServer.in(folder).set("alts.deny-if-banned", true));
        proxy.quit(proxy.online(new FakePlayer("Steve", "10.0.0.1")));
        proxy.command(proxy.console, "ban Steve -scope=survival x");

        assertTrue(proxy.join(new FakePlayer("SteveAlt", "10.0.0.1")).isEmpty());
    }

    @Test
    void aBanForSomeServersAppliedOnAnotherInstance_movesThePlayerHere() {
        final TestServer a = proxy(TestServer.in(folder.resolve("a")).set("sync.enabled", true));
        final TestServer b = proxy(TestServer.in(folder.resolve("b")).set("sync.enabled", true).sharingDatabaseWith(a));
        final FakePlayer steve = on(b, "Steve", "survival1");

        a.command(a.console, "ban Steve -scope=survival x");
        b.pollSync();

        assertEquals(Optional.of("lobby"), b.platform.serverOf(steve));
        assertEquals(0, steve.kicks());
    }

    @Test
    void spigotWithoutServerName_refusesScopes() {
        final TestServer paper = TestServer.in(folder).platform(PlatformType.BUKKIT).start();
        servers.add(paper);
        paper.online(new FakePlayer("Steve", "10.0.0.1"));

        paper.command(paper.console, "ban Steve -scope=survival x");

        assertTrue(paper.console.received("This server has no scopes.server-name"), paper.console.messages().toString());
        assertTrue(paper.punishments.all().isEmpty());
    }

    @Test
    void spigotWithServerName_refusesTheLoginOnlyForItsOwnScopes() {
        final TestServer paper = TestServer.in(folder).platform(PlatformType.BUKKIT)
                .set("scopes.server-name", "survival1")
                .set("scopes.groups.survival", List.of("survival1", "survival2")).start();
        servers.add(paper);
        paper.quit(paper.online(new FakePlayer("Steve", "10.0.0.1")));
        paper.quit(paper.online(new FakePlayer("Alex", "10.0.0.2")));
        paper.command(paper.console, "ban Steve -scope=survival2 x");
        paper.command(paper.console, "ban Alex -scope=survival y");

        assertTrue(paper.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty(), "a ban for another server of the network");
        assertTrue(paper.join(new FakePlayer("Alex", "10.0.0.2")).isPresent(), "a ban for a group with this server");
        paper.command(paper.console, "ban Steve -scope=creative z");
        assertTrue(paper.console.received("Unknown scope 'creative'"), "a Spigot/Paper server only knows itself and its groups");
    }

    @Test
    void spigotWithServerName_aMuteForAnotherServerDoesNotSilenceHere() {
        final TestServer paper = TestServer.in(folder).platform(PlatformType.BUKKIT)
                .set("scopes.server-name", "survival1")
                .set("scopes.groups.survival", List.of("survival1", "survival2")).start();
        servers.add(paper);
        final FakePlayer steve = paper.online(new FakePlayer("Steve", "10.0.0.1"));

        paper.command(paper.console, "mute Steve -scope=survival2 x");
        assertEquals(Outcome.ALLOWED, paper.chat(steve, "hi"));
        paper.command(paper.console, "mute Steve -scope=survival y");
        assertEquals(Outcome.BLOCKED_MUTED, paper.chat(steve, "hi"));
    }
}
