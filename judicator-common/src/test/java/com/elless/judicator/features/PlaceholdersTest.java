package com.elless.judicator.features;

import com.elless.judicator.placeholders.PlaceholderService;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlaceholderAPI's %judicator_...%: read from what the API gives, and looked up again when this server changes it.
 */
class PlaceholdersTest {

    @TempDir
    Path folder;

    private TestServer server;
    private PlaceholderService placeholders;

    @BeforeEach
    void start() {
        server = TestServer.in(folder).start();
        placeholders = new PlaceholderService(server.judicator, value -> value ? "yes" : "no");
    }

    @AfterEach
    void stop() {
        placeholders.close();
        server.close();
    }

    private String value(FakePlayer player, String params) {
        return placeholders.resolve(player.getUniqueId(), params);
    }

    @Test
    void cleanPlayer_hasNothingInForce() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertEquals("no", value(steve, "banned"));
        assertEquals("no", value(steve, "muted"));
        assertEquals("0", value(steve, "warns"));
        assertEquals("0", value(steve, "punishments_total"));
        assertEquals("", value(steve, "mute_reason"));
    }

    @Test
    void mute_showsItsDetailsAndGoesAwayWhenRevoked() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        assertEquals("no", value(steve, "muted"));

        server.command(server.console, "tempmute Steve 2:h spam");

        assertEquals("yes", value(steve, "muted"));
        assertEquals("spam", value(steve, "mute_reason"));
        assertEquals("Console", value(steve, "mute_punisher"));
        assertTrue(value(steve, "mute_remaining").startsWith("1 hour"), value(steve, "mute_remaining"));
        assertEquals("every server", value(steve, "mute_scope"));

        server.command(server.console, "unmute Steve");

        assertEquals("no", value(steve, "muted"));
        assertEquals("", value(steve, "mute_reason"));
        assertEquals("1", value(steve, "mutes_total"));
    }

    @Test
    void ban_ofAPlayerWhoLeft_isPermanentWithItsId() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.quit(steve);

        server.command(server.console, "ban Steve hacking");

        assertEquals("yes", value(steve, "banned"));
        assertEquals("Permanent", value(steve, "ban_expires"));
        assertEquals("Permanent", value(steve, "ban_remaining"));
        assertEquals(String.valueOf(server.punishments.findAllByUsername("steve").get(0).getId()), value(steve, "ban_id"));
    }

    @Test
    void warns_countThoseInForceAndAll() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "warn Steve one");
        server.command(server.console, "warn Steve two");
        assertEquals("2", value(steve, "warns"));

        server.command(server.console, "unwarn " + server.warns.findAllByUsername("steve").get(0).getId());

        assertEquals("1", value(steve, "warns"));
        assertEquals("1", value(steve, "warns_total"));
    }

    @Test
    void unknownPlaceholder_isNotJudicators() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertNull(value(steve, "karma"));
        assertNull(value(steve, "ban_karma"));
        assertNull(value(steve, "warn_reason"));
    }

}
