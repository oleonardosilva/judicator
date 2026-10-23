package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * alts.deny-if-banned: an alt cannot join while another account of the same IP is banned (ban evasion).
 */
class BannedAltsTest {


    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private TestServer denying(TestServer.Builder builder) {
        return builder.set("alts.deny-if-banned", true).start();
    }

    private Punishment banSteve() {
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "ban Steve x-ray");
        return server.punishments.findAllByUsername("Steve").get(0);
    }

    @Test
    void altOfABannedAccount_isRefusedAndStaffIsTold() {
        server = denying(TestServer.in(folder));
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.9", "judicator.alts.notify"));
        final Punishment ban = banSteve();

        final Optional<String> kick = server.join(new FakePlayer("SteveAlt", "10.0.0.1"));

        assertTrue(kick.isPresent());
        assertTrue(kick.get().contains("Another account that used your network is banned."), kick.get());
        assertTrue(kick.get().contains("#" + ban.getId()));
        assertFalse(kick.get().contains("Steve "), "the other account's name is not shown");
        assertTrue(staff.received("[Alts] SteveAlt was refused: shares an IP with the banned account Steve (#" + ban.getId() + ")"),
                staff.messages().toString());
    }

    @Test
    void off_byDefault() {
        server = TestServer.in(folder).start();
        banSteve();

        assertTrue(server.join(new FakePlayer("SteveAlt", "10.0.0.1")).isEmpty());
    }

    @Test
    void appliesInOfflineModeToo() {
        server = denying(TestServer.in(folder).offlineMode());
        banSteve();

        assertTrue(server.join(new FakePlayer("SteveAlt", "10.0.0.1")).isPresent());
    }

    @Test
    void exemptAddress_passes() {
        server = denying(TestServer.in(folder).set("exempt-addresses", List.of("127.0.0.1", "::1", "10.0.0.0/24")));
        banSteve();

        assertTrue(server.join(new FakePlayer("SteveAlt", "10.0.0.1")).isEmpty());
    }

    @Test
    void playerExemptFromAlts_passes() {
        final UUID sibling = UUID.nameUUIDFromBytes("OfflinePlayer:Sibling".getBytes(StandardCharsets.UTF_8));
        server = denying(TestServer.in(folder).immunePlayers("  \"" + sibling + "\": [alts]\n"));
        banSteve();

        assertTrue(server.join(new FakePlayer("Sibling", sibling, "10.0.0.1")).isEmpty());
        assertTrue(server.join(new FakePlayer("Other", "10.0.0.1")).isPresent());
    }

    @Test
    void muteOrAnEndedBan_doesNotRefuse() {
        server = denying(TestServer.in(folder));
        final Punishment ban = banSteve();
        server.command(server.console, "revoke " + ban.getId());
        server.command(server.console, "mute Steve spam");

        assertTrue(server.join(new FakePlayer("SteveAlt", "10.0.0.1")).isEmpty());
    }

    @Test
    void anotherAddress_isNotAffected() {
        server = denying(TestServer.in(folder));
        banSteve();

        assertTrue(server.join(new FakePlayer("Stranger", "10.0.0.2")).isEmpty());
    }
}
