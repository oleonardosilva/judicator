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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * /ban, /tempban and /banip: who can use them, what is stored, who is kicked, who is told and who can join after.
 */
class BanTest {

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

    private List<Punishment> activeBans(String name) {
        return server.punishments.findAllActiveByUsernameAndTypes(name, PunishType.BAN, PunishType.TEMPBAN);
    }

    @Test
    void withoutTarget_showsUsageAndBansNobody() {
        server.command(server.console, "ban");
        assertTrue(server.console.received("Incorrect command. Use: /ban"), server.console.messages().toString());
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void playerWithoutPermission_cannotUseTheCommand() {
        final FakePlayer player = server.online(new FakePlayer("Player", "10.0.0.9"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        assertFalse(server.command(player, "ban Steve griefing"));
        assertTrue(activeBans("Steve").isEmpty());
    }

    @Test
    void consoleBan_storesKicksAnnouncesAndBlocksTheNextLogin() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer bystander = server.online(new FakePlayer("Bystander", "10.0.0.2"));
        final FakePlayer staff = server.online(new FakePlayer("Helper", "10.0.0.3", "judicator.notify"));

        server.command(server.console, "ban Steve hacking with fly");

        final List<Punishment> bans = activeBans("steve");
        assertEquals(1, bans.size());
        final Punishment ban = bans.get(0);
        assertEquals(PunishType.BAN, ban.getType());
        assertEquals("hacking with fly", ban.getReason().orElseThrow());
        assertEquals("Console", ban.getPunisher());
        assertEquals(steve.getUniqueId(), ban.getPlayerUUID().orElseThrow());
        assertTrue(ban.getFinishAt().isEmpty());

        assertTrue(steve.wasKicked());
        assertTrue(steve.kickMessage().contains("You have been banned from the server."), steve.kickMessage());
        assertTrue(steve.kickMessage().contains("Reason: hacking with fly"), steve.kickMessage());
        assertTrue(bystander.received("User Steve was banned by Console."), bystander.messages().toString());
        assertTrue(staff.received("[Staff] Console applied BAN to Steve: hacking with fly"), staff.messages().toString());
        assertFalse(bystander.received("[Staff]"));

        server.quit(steve);
        final Optional<String> refused = server.join(new FakePlayer("Steve", "10.0.0.1"));
        assertTrue(refused.isPresent());
        assertTrue(refused.get().contains("Reason: hacking with fly"));
    }

    @Test
    void banOfPlayerWhoLeft_usesTheUuidSeenOnJoin() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.quit(steve);

        server.command(server.console, "ban steve alt account");

        final Punishment ban = activeBans("Steve").get(0);
        assertEquals(steve.getUniqueId(), ban.getPlayerUUID().orElseThrow());
        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.50")).isPresent());
    }

    @Test
    void staffBan_waitsForEvidenceAndConfirmation() {
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertTrue(server.command(mod, "ban Steve xray"));
        assertTrue(activeBans("Steve").isEmpty(), "nothing is applied before confirming");
        assertFalse(steve.wasKicked());

        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.chat(mod, "https://imgur.com/proof1"));
        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.chat(mod, "confirm"));

        final Punishment ban = activeBans("Steve").get(0);
        assertEquals("Mod", ban.getPunisher());
        assertEquals(List.of("https://imgur.com/proof1"), ban.getEvidences());
        assertTrue(steve.wasKicked());
        assertTrue(steve.kickMessage().contains("Evidence: https://imgur.com/proof1"), steve.kickMessage());
        assertTrue(mod.received("The punishment has been successfully applied to the player."));
        // The prompt is over: the next message is normal chat again.
        assertEquals(TestServer.Outcome.ALLOWED, server.chat(mod, "hello"));
    }

    @Test
    void staffBan_canBeCancelledByTypingCancelOrRunningAnotherCommand() {
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "ban Steve xray");
        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.chat(mod, "cancel"));
        assertTrue(mod.received("Operation successfully canceled."));

        server.command(mod, "ban Steve xray");
        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.playerCommand(mod, "/spawn"));

        assertTrue(activeBans("Steve").isEmpty());
        assertEquals(TestServer.Outcome.ALLOWED, server.chat(mod, "confirm"));
    }

    @Test
    void tempban_rejectsAnInvalidDurationAndStoresTheEnd() {
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(server.console, "tempban Steve two_hours spam");
        assertTrue(server.console.received("Incorrect command. Use: /tempban"), server.console.messages().toString());
        assertTrue(activeBans("Steve").isEmpty());

        final LocalDateTime before = LocalDateTime.now();
        server.command(server.console, "tempban Steve 2:h spam");
        final LocalDateTime after = LocalDateTime.now();

        final Punishment ban = activeBans("Steve").get(0);
        assertEquals(PunishType.TEMPBAN, ban.getType());
        final LocalDateTime end = ban.getFinishAt().orElseThrow();
        assertFalse(end.isBefore(before.plusHours(2).withNano(0)), end.toString());
        assertFalse(end.isAfter(after.plusHours(2)), end.toString());
    }

    @Test
    void expiredTempban_doesNotBlockTheLogin() {
        final LocalDateTime start = LocalDateTime.now().minusDays(2);
        server.punishments.save(Punishment.create(null, "old", "Console", "Steve", null,
                start, start.plusDays(1), List.of(), PunishType.TEMPBAN));

        assertTrue(server.join(new FakePlayer("Steve", "10.0.0.1")).isEmpty());
    }

    @Test
    void banip_kicksEveryAccountOnTheAddressAndBlocksNewOnes() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.2"));

        server.command(server.console, "banip Steve ddos");

        final Punishment ban = activeBans("Steve").get(0);
        assertEquals("10.0.0.1", ban.getIpAddress().orElseThrow());
        assertTrue(steve.wasKicked());
        assertTrue(alt.wasKicked());
        assertFalse(other.wasKicked());
        assertTrue(server.join(new FakePlayer("BrandNew", "10.0.0.1")).isPresent(), "same address");
        assertTrue(server.join(new FakePlayer("Steve", "10.9.9.9")).isPresent(), "same name, other address");
        assertTrue(server.join(new FakePlayer("Someone", "10.0.0.3")).isEmpty());
    }

    @Test
    void banipOfAPlayerWithNoKnownAddress_warnsAndBansByName() {
        server.command(server.console, "banip Ghost ddos");

        assertTrue(server.console.received("The player's IP was not found in the database"), server.console.messages().toString());
        final Punishment ban = activeBans("Ghost").get(0);
        assertTrue(ban.getIpAddress().isEmpty());
        assertTrue(ban.getPlayerUUID().isEmpty(), "never joined and no Mojang lookup: no UUID");
    }

    @Test
    void banipOfAPlayerWhoLeft_usesTheLastKnownAddress() {
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));

        server.command(server.console, "banip Steve ddos");

        assertEquals("10.0.0.1", activeBans("Steve").get(0).getIpAddress().orElseThrow());
        assertTrue(server.join(new FakePlayer("Friend", "10.0.0.1")).isPresent());
    }

    @Test
    void onlineMode_banFollowsTheAccountAfterANameChange() {
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "ban Steve hacking");

        final Optional<String> refused = server.join(new FakePlayer("Steven", steve.getUniqueId(), "10.0.0.77"));

        assertTrue(refused.isPresent(), "same account (UUID), new name and address");
    }

    @Test
    void evidenceThatDoesNotFit_isRefusedAndThePunishmentStillWorks() {
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "ban Steve xray");
        final String link = "https://example.com/" + "a".repeat(100);

        server.chat(mod, link);
        server.chat(mod, link + "b");
        mod.clearMessages();
        server.chat(mod, link + "c");
        assertTrue(mod.received("There is no room for this evidence"), mod.messages().toString());
        server.chat(mod, "confirm");

        final Punishment ban = activeBans("Steve").get(0);
        assertEquals(List.of(link, link + "b"), ban.getEvidences());
    }
}
