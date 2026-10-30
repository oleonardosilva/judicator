package com.elless.judicator.features;

import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accounts sharing an IP: /alts and the warning to staff when someone joins from the address of a punished account.
 */
class AltsTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void alts_listsOtherAccountsWithTheirStatus() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.quit(server.online(new FakePlayer("SteveAlt", "10.0.0.1")));
        server.quit(server.online(new FakePlayer("Muted", "10.0.0.1")));
        server.quit(server.online(new FakePlayer("Stranger", "10.0.0.2")));
        server.command(server.console, "ban SteveAlt evasion");
        server.command(server.console, "mute Muted spam");
        server.console.clearMessages();

        server.command(server.console, "alts Steve");

        final String text = String.join("\n", server.console.messages());
        assertTrue(text.contains("Accounts that shared an IP with Steve:"), text);
        assertTrue(text.contains("SteveAlt - banned"), text);
        assertTrue(text.contains("Muted - muted"), text);
        assertFalse(text.contains("Stranger"), text);
        assertFalse(text.contains(" Steve -"), "the player is not their own alt");
    }

    @Test
    void alts_ofAPlayerWithNoSharedAddress() {
        server = TestServer.in(folder).start();
        server.quit(server.online(new FakePlayer("Lonely", "10.0.0.9")));
        server.command(server.console, "alts Lonely");
        assertTrue(server.console.received("No other accounts were found for Lonely."));
    }

    @Test
    void staffIsWarnedWhenSomeoneJoinsFromTheAddressOfABannedAccount() {
        server = TestServer.in(folder).start();
        final FakePlayer watcher = server.online(new FakePlayer("Watcher", "10.0.0.5", "judicator.alts.notify"));
        final FakePlayer plain = server.online(new FakePlayer("Plain", "10.0.0.6"));
        server.quit(server.online(new FakePlayer("Banned", "10.0.0.1")));
        server.command(server.console, "ban Banned hacking");

        server.online(new FakePlayer("Fresh", "10.0.0.1"));

        assertTrue(watcher.received("[Alts] Fresh shares an IP with banned/muted account(s): Banned"), watcher.messages().toString());
        assertFalse(plain.received("[Alts]"));
    }

    @Test
    void noWarningForCleanAccountsOrWhenDisabled() {
        server = TestServer.in(folder).start();
        final FakePlayer watcher = server.online(new FakePlayer("Watcher", "10.0.0.5", "judicator.alts.notify"));
        server.quit(server.online(new FakePlayer("Brother", "10.0.0.1")));
        server.online(new FakePlayer("Sister", "10.0.0.1"));
        assertFalse(watcher.received("[Alts]"));
        server.close();

        server = TestServer.in(folder.resolve("off")).set("alts.notify-on-join", false).start();
        final FakePlayer watcher2 = server.online(new FakePlayer("Watcher", "10.0.0.5", "judicator.alts.notify"));
        server.quit(server.online(new FakePlayer("Banned", "10.0.0.1")));
        server.command(server.console, "ban Banned hacking");
        server.online(new FakePlayer("Fresh", "10.0.0.1"));
        assertFalse(watcher2.received("[Alts]"));
    }
}
