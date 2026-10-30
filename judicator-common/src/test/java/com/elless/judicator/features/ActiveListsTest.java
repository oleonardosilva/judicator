package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /banlist and /mutelist: what is in force right now.
 */
class ActiveListsTest {

    @TempDir
    Path folder;

    private TestServer server;

    @BeforeEach
    void start() {
        server = TestServer.in(folder).start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private Punishment save(String nickname, String address, PunishType type, LocalDateTime start, LocalDateTime end) {
        return server.punishments.save(Punishment.create(null, "reason of " + nickname, "Mod", nickname, address, start, end, List.of(), type));
    }

    @Test
    void banlist_showsOnlyBansInForceNewestFirst() {
        final LocalDateTime now = LocalDateTime.now();
        save("Permanent", null, PunishType.BAN, now.minusDays(2), null);
        save("Temporary", null, PunishType.TEMPBAN, now.minusHours(1), now.plusDays(1));
        save("Expired", null, PunishType.TEMPBAN, now.minusDays(3), now.minusDays(1));
        final Punishment revoked = save("Revoked", null, PunishType.BAN, now.minusHours(2), null);
        server.punishments.revoke(revoked.getId(), "appeal");
        save("Muted", null, PunishType.MUTE, now.minusHours(1), null);
        save("10.0.0.9", "10.0.0.9", PunishType.BAN, now.minusHours(3), null);

        server.command(server.console, "banlist");

        final String text = String.join("", server.console.messages());
        assertTrue(text.contains("Bans in force (3 in total, page 1/1)"), text);
        assertTrue(text.contains("Temporary - TEMPBAN"), text);
        assertTrue(text.contains("Permanent - BAN - until Permanent"), text);
        assertTrue(text.contains("10.0.0.9 - BAN"), "IP bans are listed too: " + text);
        assertTrue(text.indexOf("Temporary") < text.indexOf("10.0.0.9") && text.indexOf("10.0.0.9") < text.indexOf("Permanent"), text);
        assertFalse(text.contains("Expired") || text.contains("Revoked") || text.contains("Muted"), text);
        assertFalse(text.contains("Page"), "one page, no navigation: " + text);
    }

    @Test
    void mutelist_showsOnlyMutesInForce() {
        final LocalDateTime now = LocalDateTime.now();
        save("Muted", null, PunishType.TEMPMUTE, now.minusHours(1), now.plusHours(1));
        save("Banned", null, PunishType.BAN, now.minusHours(1), null);

        server.command(server.console, "mutelist");

        final String text = String.join("", server.console.messages());
        assertTrue(text.contains("Mutes in force (1 in total, page 1/1)"), text);
        assertTrue(text.contains("Muted - TEMPMUTE"), text);
        assertFalse(text.contains("Banned"), text);
    }

    @Test
    void entriesShowDetailsOnHoverWithoutTheIpAndOpenThePunishmentOnClick() {
        final Punishment ban = save("10.0.0.9", "10.0.0.9", PunishType.BAN, LocalDateTime.now(), null);

        server.command(server.console, "banlist");

        assertTrue(server.console.clicks().contains("/pview " + ban.getId()), server.console.clicks().toString());
        final String hover = String.join("", server.console.hovers());
        assertTrue(hover.contains("Reason: reason of 10.0.0.9") && hover.contains("Author: Mod"), hover);
        assertFalse(hover.contains("IP"), "the address is only shown by /pview, behind its own permission: " + hover);
    }

    @Test
    void longListsArePagedWithClickableNavigation() {
        final LocalDateTime start = LocalDateTime.now().minusDays(30);
        for (int i = 1; i <= 25; i++) {
            save("Player" + i, null, PunishType.BAN, start.plusDays(i), null);
        }

        server.command(server.console, "banlist");
        String text = String.join("", server.console.messages());
        assertTrue(text.contains("(25 in total, page 1/3)"), text);
        assertTrue(text.contains("Player25") && !text.contains("Player15 "), text);
        assertTrue(text.contains("| Page 1/3 | Next »"), text);
        assertEquals(List.of("/banlist 2"), server.console.clicks().stream().filter(c -> c.startsWith("/banlist")).toList());

        server.console.clearMessages();
        server.command(server.console, "banlist 2");
        text = String.join("", server.console.messages());
        assertTrue(text.contains("« Previous | Page 2/3 | Next »"), text);
        assertEquals(List.of("/banlist 1", "/banlist 3"), server.console.clicks().stream().filter(c -> c.startsWith("/banlist")).toList());

        server.console.clearMessages();
        server.command(server.console, "banlist 99");
        text = String.join("", server.console.messages());
        assertTrue(text.contains("page 3/3") && text.contains("Player1 -") && text.contains("Player5 -"), text);
    }

    @Test
    void emptyListsAndInvalidPages() {
        server.command(server.console, "banlist");
        server.command(server.console, "mutelist");
        server.command(server.console, "banlist two");

        assertTrue(server.console.received("There are no bans in force."));
        assertTrue(server.console.received("There are no mutes in force."));
        assertTrue(server.console.received("Incorrect command. Use: /banlist [page]."));
    }

    @Test
    void eachListNeedsItsPermission() {
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban"));
        assertFalse(server.command(helper, "banlist"));
        assertFalse(server.command(helper, "mutelist"));
        helper.grant("judicator.banlist");
        assertTrue(server.command(helper, "banlist"));
        assertFalse(server.command(helper, "mutelist"));
        helper.grant("judicator.mutelist");
        assertTrue(server.command(helper, "mutelist"));
    }
}
