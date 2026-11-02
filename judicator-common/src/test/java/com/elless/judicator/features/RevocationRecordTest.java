package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What undoes or changes a punishment leaves a record: who revoked it and when (stored and shown), and an embed in
 * Discord for revocations, edits, warns removed and rollbacks. Revocations also count for staff-rate-limit.
 */
class RevocationRecordTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private Punishment only(String name) {
        final List<Punishment> all = server.punishments.findAllByUsername(name);
        assertEquals(1, all.size(), all.toString());
        return all.get(0);
    }

    private void ban(FakePlayer staff, String target) {
        server.command(staff, "ban " + target + " hacking");
        assertEquals(Outcome.TAKEN_BY_PROMPT, server.chat(staff, "confirm"));
    }

    private List<String> posts(String title) {
        return server.discordPosts.stream().filter(json -> json.contains(title)).toList();
    }

    // --- who revoked and when ---

    @Test
    void anUnban_keepsWhoRevokedAndWhen_andShowsIt() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.unban", "judicator.view"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        ban(mod, "Steve");

        server.command(mod, "unban Steve appeal accepted");

        final Punishment ban = only("Steve");
        assertEquals("Mod", ban.getRevokedBy().orElseThrow());
        assertEquals(mod.getUniqueId(), ban.getRevokedByUuid().orElseThrow());
        assertTrue(ban.getRevokedAt().isPresent());
        server.command(mod, "punishview " + ban.getId());
        assertTrue(mod.received("Revoked: Yes (by Mod, "), mod.messages().toString());
    }

    @Test
    void theConsoleAndAPlugin_areStoredByName_withoutUuid() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.3"));
        server.command(server.console, "ban Steve hacking");
        server.command(server.console, "ban Alex hacking");

        server.command(server.console, "revoke " + only("Steve").getId() + " mistake");
        server.judicator.getApi().revoke(only("Alex").getId(), "appeal", "AntiCheat").join();

        assertEquals("Console", only("Steve").getRevokedBy().orElseThrow());
        assertTrue(only("Steve").getRevokedByUuid().isEmpty());
        assertEquals("AntiCheat", only("Alex").getRevokedBy().orElseThrow());
    }

    @Test
    void aPunishmentInForce_showsNoRevoker() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.view"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        ban(mod, "Steve");

        server.command(mod, "punishview " + only("Steve").getId());

        assertTrue(mod.messages().stream().anyMatch(line -> line.contains("Revoked: No\n") && !line.contains("(by ")),
                mod.messages().toString());
    }

    @Test
    void aRollback_storesWhoRolledBack() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban"));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.3", "judicator.admin"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        ban(mod, "Steve");

        server.command(admin, "staffrollback Mod 1:h confirm");

        assertEquals("Admin", only("Steve").getRevokedBy().orElseThrow());
    }

    // --- Discord ---

    @Test
    void revocationsEditsAndRemovedWarns_arePostedToDiscord() {
        server = TestServer.in(folder).discord().start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempban Steve 1:d hacking");
        final long id = only("Steve").getId();

        server.command(server.console, "extend " + id + " 1:h");
        server.command(server.console, "unban Steve appeal");
        server.command(server.console, "warn Steve spam");
        server.command(server.console, "unwarn " + server.warns.all().get(0).getId());

        assertEquals(1, posts("Punishment Changed").size(), server.discordPosts.toString());
        final List<String> revoked = posts("Punishment Revoked");
        assertEquals(1, revoked.size(), server.discordPosts.toString());
        assertTrue(revoked.get(0).contains("was revoked by **Console**") && revoked.get(0).contains("Revoked by")
                && revoked.get(0).contains("appeal"), revoked.get(0));
        assertTrue(posts("Warning Removed").get(0).contains("removed by **Console**"), server.discordPosts.toString());
    }

    @Test
    void aRollback_isPostedOnce() {
        server = TestServer.in(folder).discord().start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.warn"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.3"));
        ban(mod, "Steve");
        ban(mod, "Alex");
        server.command(mod, "warn Steve spam");

        server.command(server.console, "staffrollback Mod 1:h confirm");

        final List<String> rollback = posts("Staff Rollback");
        assertEquals(1, rollback.size(), server.discordPosts.toString());
        assertTrue(rollback.get(0).contains("2 punishment(s) revoked, 1 warning(s) deleted"), rollback.get(0));
        assertTrue(posts("Punishment Revoked").isEmpty(), "no embed per punishment of the rollback");
        assertTrue(posts("Warning Removed").isEmpty());
    }

    // --- staff-rate-limit ---

    @Test
    void revocations_haveADelayToo() {
        server = TestServer.in(folder).set("staff-rate-limit.enabled", true).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.unban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.3"));
        server.command(server.console, "ban Steve hacking");
        server.command(server.console, "ban Alex hacking");

        server.command(mod, "unban Steve");
        server.command(mod, "unban Alex");

        assertTrue(mod.received("Wait 5s before applying another revoke."), mod.messages().toString());
        assertFalse(only("Alex").isRevoked());
    }

    @Test
    void anUnbanOfNobody_doesNotCount() {
        server = TestServer.in(folder).set("staff-rate-limit.enabled", true).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.unban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "ban Steve hacking");

        server.command(mod, "unban Nobody");
        server.command(mod, "unban Steve");

        assertTrue(only("Steve").isRevoked(), mod.messages().toString());
    }

    @Test
    void aLockedStaffMember_cannotRevokeEither() {
        server = TestServer.in(folder).set("staff-rate-limit.enabled", true)
                .set("staff-rate-limit.delay.ban", 0).set("staff-rate-limit.max.ban", 1).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.ban", "judicator.unban"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("Alex", "10.0.0.3"));
        server.online(new FakePlayer("Bob", "10.0.0.4"));
        server.command(server.console, "ban Bob hacking");
        ban(mod, "Steve");
        server.command(mod, "ban Alex hacking"); // past the max: locked

        server.command(mod, "unban Bob");

        assertTrue(mod.received("You are locked out of punishing"), mod.messages().toString());
        assertFalse(only("Bob").isRevoked());
    }
}
