package com.openmc.judicator.features;

import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline-mode servers skip the UUID login check, so mutes must be restored from the name/IP check alone.
 */
class MuteOfflineModeTest {

    @Test
    void trustUuids_checksBansByUuidOnABackendBehindAProxy(@TempDir Path backend) {
        try (TestServer proxied = TestServer.in(backend).offlineMode().set("trust-uuids", true).start()) {
            final UUID account = UUID.randomUUID();
            proxied.online(new FakePlayer("Steve", account, "10.0.0.1"));
            proxied.command(proxied.console, "ban Steve xray");

            assertTrue(proxied.join(new FakePlayer("Steve2", account, "10.0.0.2")).isPresent(), "renamed, new IP, same UUID");
        }
        try (TestServer offline = TestServer.in(backend.resolve("plain")).offlineMode().start()) {
            final UUID account = UUID.randomUUID();
            offline.online(new FakePlayer("Steve", account, "10.0.0.1"));
            offline.command(offline.console, "ban Steve xray");

            assertTrue(offline.join(new FakePlayer("Steve2", account, "10.0.0.2")).isEmpty(), "a real offline server cannot trust UUIDs");
        }
    }

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
