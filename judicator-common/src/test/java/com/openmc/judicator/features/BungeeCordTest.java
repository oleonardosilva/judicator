package com.openmc.judicator.features;

import com.openmc.judicator.platform.PlatformType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On BungeeCord the core behaves as on Velocity: it is the proxy. The proxy has lobby, survival1, survival2 and
 * creative; the group "survival" is survival1 and survival2; the try list (priorities) is lobby, creative.
 */
class BungeeCordTest {

    @TempDir
    Path folder;

    private TestServer proxy;

    @BeforeEach
    void start() {
        proxy = TestServer.in(folder).platform(PlatformType.BUNGEECORD)
                .set("scopes.groups.survival", List.of("survival1", "survival2"))
                .start();
        proxy.platform.servers(List.of("lobby", "survival1", "survival2", "creative"), List.of("lobby", "creative"));
    }

    @AfterEach
    void stop() {
        proxy.close();
    }

    private FakePlayer on(String name, String where) {
        final FakePlayer player = proxy.online(new FakePlayer(name, "10.0.0." + (name.length() + 1)));
        proxy.platform.moveTo(player, where);
        return player;
    }

    @Test
    void aBanForAGroup_keepsThePlayerOutOfItsServersOnly() {
        final FakePlayer steve = on("Steve", "lobby");

        proxy.command(proxy.console, "ban Steve -scope=survival hacking");

        assertEquals(0, steve.kicks());
        assertFalse(proxy.switchServer(steve, "survival1"));
        assertTrue(proxy.switchServer(steve, "creative"));
    }

    @Test
    void aBanForTheServerThePlayerIsOn_sendsThemToTheTryList() {
        final FakePlayer steve = on("Steve", "lobby");

        proxy.command(proxy.console, "ban Steve -scope=lobby griefing");

        assertEquals(0, steve.kicks());
        assertEquals(Optional.of("creative"), proxy.platform.serverOf(steve));
    }

    @Test
    void proxyModeIsIgnored() {
        try (TestServer configured = TestServer.in(folder.resolve("configured")).platform(PlatformType.BUNGEECORD)
                .set("proxy-mode", true).start()) {
            assertFalse(configured.judicator.isProxyMode());
        }
    }

    @Test
    void theReportsNoticeWaitsForTheFirstServer() {
        final FakePlayer alex = on("Alex", "lobby");
        on("Steve", "lobby");
        proxy.command(alex, "report Steve x");
        assertEquals(1, proxy.reports.all().size(), alex.messages().toString());

        final FakePlayer mod = proxy.online(new FakePlayer("Mod", "10.0.0.9", "judicator.reports.notify"));

        assertFalse(mod.received("[Reports]"), mod.messages().toString());
    }

}
