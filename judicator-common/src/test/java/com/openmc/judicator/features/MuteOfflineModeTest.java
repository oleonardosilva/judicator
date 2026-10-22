package com.openmc.judicator.features;

import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Offline-mode servers skip the UUID login check, so mutes must be restored from the name/IP check alone.
 */
class MuteOfflineModeTest {

    @TempDir
    Path folder;

    private TestServer server;

    @BeforeEach
    void start() {
        server = TestServer.in(folder).offlineMode().start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void nameMute_survivesRelog() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");
        server.quit(steve);

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(server.online(new FakePlayer("Steve", "10.0.0.1")), "back"));
    }

    @Test
    void ipMute_survivesRelog() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "muteip Steve spam");
        server.quit(steve);

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(server.online(new FakePlayer("Steve", "10.0.0.1")), "back"));
        assertEquals(Outcome.BLOCKED_MUTED, server.chat(server.online(new FakePlayer("Other", "10.0.0.1")), "hi"));
    }
}
