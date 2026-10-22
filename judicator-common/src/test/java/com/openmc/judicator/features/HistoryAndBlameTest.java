package com.openmc.judicator.features;

import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
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
 * /phistory, /pview and /blame.
 */
class HistoryAndBlameTest {

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

    @Test
    void historyOfACleanPlayer() {
        server.command(server.console, "phistory Angel");
        assertTrue(server.console.received("This player has no punishments."));
    }

    @Test
    void historyListsEveryPunishmentAndViewShowsDetails() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");
        server.command(server.console, "ban Steve hacking");
        final List<Punishment> all = server.punishments.all();
        server.console.clearMessages();

        server.command(server.console, "phistory steve");

        final String history = String.join("", server.console.messages());
        assertTrue(history.contains("Punishments for player steve"), history);
        assertTrue(history.contains("#" + all.get(0).getId() + " - MUTE"), history);
        assertTrue(history.contains("#" + all.get(1).getId() + " - BAN"), history);

        server.command(server.console, "pview " + all.get(1).getId());
        assertTrue(server.console.received("Punishment ID: #" + all.get(1).getId()));
        assertTrue(server.console.received("Player: Steve"));
        assertTrue(server.console.received("Reason: hacking"));
        assertTrue(server.console.received("Status: Active"));
        // The configured "No" of messages_en.yml (the unquoted yes/no keys used to load as booleans, so the
        // hard-coded fallback "No." was shown instead).
        assertTrue(server.console.received("Revoked: No\n"), server.console.messages().toString());

        server.command(server.console, "revoke " + all.get(1).getId() + " mistake");
        server.console.clearMessages();
        server.command(server.console, "pview " + all.get(1).getId());
        assertTrue(server.console.received("Status: Revoked"));
        assertTrue(server.console.received("Revoked: Yes\n"));
        assertTrue(server.console.received("Revocation Reason: mistake"));
    }

    @Test
    void viewOfAnUnknownId() {
        server.command(server.console, "pview 42");
        assertTrue(server.console.received("No punishment was found with the provided ID."));
        server.command(server.console, "pview");
        assertTrue(server.console.received("Incorrect command. Use: /pview (id)."));
    }

    @Test
    void blame_pagesTheStaffMembersPunishmentsNewestFirst() {
        final LocalDateTime start = LocalDateTime.now().minusDays(20);
        for (int i = 1; i <= 12; i++) {
            server.punishments.save(Punishment.create(null, "r" + i, "Mod", "Player" + i, null, start.plusDays(i), null,
                    List.of(), PunishType.BAN));
        }
        server.punishments.save(Punishment.create(null, "x", "OtherMod", "Someone", null, start, null, List.of(), PunishType.BAN));

        server.command(server.console, "blame mod");
        final String page1 = String.join("", server.console.messages());
        assertTrue(page1.contains("Punishments applied by mod (12 in total, page 1/2)"), page1);
        assertTrue(page1.indexOf("Player12") < page1.indexOf("Player3"), "newest first");
        assertFalse(page1.contains("Player2 -"), page1);
        assertFalse(page1.contains("Someone"));
        assertTrue(page1.contains("Next page: /blame mod 2"));

        server.console.clearMessages();
        server.command(server.console, "staffhistory Mod 2");
        final String page2 = String.join("", server.console.messages());
        assertTrue(page2.contains("page 2/2"), page2);
        assertTrue(page2.contains("Player2 -") && page2.contains("Player1 -"), page2);
        assertFalse(page2.contains("Next page"));
    }

    @Test
    void blame_pageOutOfRangeShowsTheNearestPage() {
        for (int i = 1; i <= 12; i++) {
            server.punishments.save(Punishment.create(null, "r", "Mod", "Player" + i, null, LocalDateTime.now().minusDays(i), null,
                    List.of(), PunishType.BAN));
        }
        server.command(server.console, "blame Mod 0");
        assertTrue(server.console.received("page 1/2"), server.console.messages().toString());
        server.command(server.console, "blame Mod 99");
        assertTrue(server.console.received("page 2/2"), server.console.messages().toString());
        server.command(server.console, "blame Mod -3");
        assertEquals(2, server.console.messages().stream().filter(m -> m.contains("page 1/2")).count());
    }

    @Test
    void blame_invalidInput() {
        server.command(server.console, "blame");
        assertTrue(server.console.received("Incorrect command. Use: /blame (staff) [page]."));
        server.command(server.console, "blame Mod two");
        assertEquals(2, server.console.messages().stream().filter(m -> m.contains("Use: /blame")).count());
        server.command(server.console, "blame Nobody");
        assertTrue(server.console.received("Nobody has not applied any punishment."));
    }

    @Test
    void blame_needsItsPermission() {
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.history"));
        assertFalse(server.command(helper, "blame Mod"));
        helper.grant("judicator.blame");
        assertTrue(server.command(helper, "blame Mod"));
    }
}
