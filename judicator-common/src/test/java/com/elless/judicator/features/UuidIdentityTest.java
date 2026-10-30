package com.elless.judicator.features;

import com.elless.judicator.punish.Punishment;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.testing.TestServer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Online mode: the account (UUID) is the player, the name only shows it. Name changes keep a player's punishments,
 * alts, staff level and staff history, and a name that changed hands brings nothing to its new owner.
 */
class UuidIdentityTest {

    @TempDir
    Path folder;

    private TestServer server;
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private TestServer start() {
        server = TestServer.in(folder).start();
        return server;
    }

    @Test
    void aBannedPlayerWhoChangesNameIsStillRefused() {
        start();
        server.quit(server.online(new FakePlayer("Steve", first, "10.0.0.1")));
        server.command(server.console, "ban Steve hacking");

        assertTrue(server.join(new FakePlayer("Alex", first, "10.0.0.2")).isPresent(), "same account, new name");
    }

    @Test
    void theNewOwnerOfABannedOrMutedName_joinsAndTalks() {
        start();
        server.quit(server.online(new FakePlayer("Steve", first, "10.0.0.1")));
        server.command(server.console, "mute Steve spam");
        server.command(server.console, "ban Steve hacking");
        // the banned account takes another name; somebody else takes "Steve"
        server.join(new FakePlayer("Alex", first, "10.0.0.1"));

        final FakePlayer newSteve = server.online(new FakePlayer("Steve", second, "10.0.0.9"));

        assertEquals(Outcome.ALLOWED, server.chat(newSteve, "hello"));
    }

    @Test
    void aPunishmentOfANameNobodyHadSeen_followsTheAccountThatJoinsWithIt() {
        start();
        server.command(server.console, "ban Ghost evasion");
        final Punishment ban = server.punishments.all().get(0);
        assertTrue(ban.getPlayerUUID().isEmpty(), "no account known for the name yet");

        assertTrue(server.join(new FakePlayer("Ghost", first, "10.0.0.1")).isPresent());

        assertEquals(first, server.punishments.findById(ban.getId()).orElseThrow().getPlayerUUID().orElseThrow());
        assertTrue(server.join(new FakePlayer("Phantom", first, "10.0.0.1")).isPresent(), "the account keeps it after a name change");
        assertTrue(server.join(new FakePlayer("Ghost", second, "10.0.0.2")).isEmpty(), "the next owner of the name does not get it");
    }

    @Test
    void aMuteOfANameNobodyHadSeen_holdsTheAccountThatJoinsWithIt() {
        start();
        server.command(server.console, "mute Ghost spam");

        final FakePlayer ghost = server.online(new FakePlayer("Ghost", first, "10.0.0.1"));

        assertEquals(Outcome.BLOCKED_MUTED, server.chat(ghost, "hi"));
    }

    @Test
    void theHistoryOfTheNewNameShowsWhatTheOldOneGot() {
        start();
        server.quit(server.online(new FakePlayer("Steve", first, "10.0.0.1")));
        server.command(server.console, "kick Steve calm down");
        server.command(server.console, "mute Steve spam");
        server.quit(server.online(new FakePlayer("Alex", first, "10.0.0.1")));
        server.console.clearMessages();

        server.command(server.console, "phistory Alex");

        final String history = String.join("", server.console.messages());
        assertTrue(history.contains("MUTE"), history);
        server.console.clearMessages();
        server.command(server.console, "unmute Alex");
        assertTrue(server.punishments.all().stream().filter(Punishment::isMuteType).allMatch(Punishment::isRevoked),
                "/unmute with the new name lifts the mute given to the old one");
    }

    @Test
    void theWarnHistoryOfTheNewNameShowsTheWarnsOfTheOldOne() {
        start();
        server.quit(server.online(new FakePlayer("Steve", first, "10.0.0.1")));
        server.command(server.console, "warn Steve caps");
        server.quit(server.online(new FakePlayer("Alex", first, "10.0.0.1")));
        server.console.clearMessages();

        server.command(server.console, "whistory Alex");

        assertFalse(server.console.received("This player has no punishments."), server.console.messages().toString());
        assertTrue(String.join("", server.console.messages()).contains("caps"), server.console.messages().toString());
    }

    @Test
    void anIpPunishmentChecksTheAltsAsAccounts_notTheCurrentOwnersOfTheirNames() {
        server = TestServer.in(folder).immunePlayers("  \"" + first + "\": [ban]\n").start();
        server.quit(server.online(new FakePlayer("Bob", first, "10.0.0.1")));
        // the immune account took another name; someone else is "Bob" now
        server.quit(server.online(new FakePlayer("Robert", first, "10.0.0.8")));
        server.quit(server.online(new FakePlayer("Bob", second, "10.0.0.9")));
        server.online(new FakePlayer("Target", "10.0.0.1"));

        server.command(server.console, "banip Target evasion");

        assertTrue(server.console.received("This player cannot be punished."), server.console.messages().toString());
        assertTrue(server.punishments.all().isEmpty());
    }

    @Test
    void twoAccountsThatSwappedNames_areNotEachOthersAlts() {
        start();
        server.quit(server.online(new FakePlayer("Steve", first, "10.0.0.1")));
        server.quit(server.online(new FakePlayer("Alex", second, "10.0.0.2")));
        // they swap names
        server.quit(server.online(new FakePlayer("Alex", first, "10.0.0.1")));
        server.quit(server.online(new FakePlayer("Steve", second, "10.0.0.2")));

        server.command(server.console, "alts Alex");

        assertTrue(server.console.received("No other accounts were found for Alex."), server.console.messages().toString());
    }

    @Test
    void aStaffLevelFollowsTheAccountAfterANameChange() {
        start();
        server.quit(server.online(new FakePlayer("Mod", first, "10.0.0.1", "judicator.level.50")));
        server.quit(server.online(new FakePlayer("Moderator", first, "10.0.0.1", "judicator.level.50")));
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.ban", "judicator.level.10"));

        server.command(helper, "ban Moderator abuse");

        assertTrue(helper.received("You cannot punish a staff member of equal or higher rank."), helper.messages().toString());
    }

    @Test
    void blameFindsWhatAStaffMemberAppliedUnderTheirOldName() {
        start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", first, "10.0.0.1", "judicator.ban"));
        server.online(new FakePlayer("Griefer", "10.0.0.3"));
        server.command(mod, "ban Griefer grief");
        server.chat(mod, "confirm");
        assertEquals(first, server.punishments.all().get(0).getPunisherUuid().orElseThrow());
        server.quit(mod);
        server.quit(server.online(new FakePlayer("Moderator", first, "10.0.0.1")));
        server.console.clearMessages();

        server.command(server.console, "blame Moderator");

        assertTrue(server.console.received("Griefer"), server.console.messages().toString());
        assertFalse(server.console.received("has not applied"), server.console.messages().toString());
    }

    @Test
    void offlineMode_theNameIsThePlayer() {
        server = TestServer.in(folder).offlineMode().start();
        server.quit(server.online(new FakePlayer("Steve", "10.0.0.1")));
        server.command(server.console, "ban Steve hacking");

        assertTrue(server.join(new FakePlayer("Steve", second, "10.0.0.2")).isPresent(), "anyone can join with any UUID in offline mode");
        assertTrue(server.join(new FakePlayer("steve", "10.0.0.3")).isPresent());
    }
}
