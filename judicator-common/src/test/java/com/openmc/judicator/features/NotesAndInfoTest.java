package com.openmc.judicator.features;

import com.openmc.judicator.notes.Note;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Staff notes (/note, /notes, /delnote, the warning when a player with notes joins) and /playerinfo.
 */
class NotesAndInfoTest {

    @TempDir
    Path folder;

    private TestServer server;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    @Test
    void note_isStoredWithAuthorAndUuid() {
        server = TestServer.in(folder).start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.note"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "note steve suspicious fly, keep an eye");

        final Note note = server.notes.all().get(0);
        assertEquals("Steve", note.getNickname(), "the online player's name, as written in game");
        assertEquals(steve.getUniqueId(), note.getPlayerUUID().orElseThrow());
        assertEquals("Mod", note.getAuthor());
        assertEquals("suspicious fly, keep an eye", note.getContent());
        assertTrue(mod.received("Note #" + note.getId() + " added to Steve."));
        assertFalse(steve.received("suspicious"), "the player never sees it");
    }

    @Test
    void note_refusesEmptyAndTooLongText() {
        server = TestServer.in(folder).start();

        server.command(server.console, "note Steve");
        server.command(server.console, "note Steve " + "x".repeat(256));

        assertTrue(server.console.received("Incorrect command. Use: /note (player) (text)."));
        assertTrue(server.console.received("A note can have at most 255 characters."));
        assertTrue(server.notes.all().isEmpty());
    }

    @Test
    void notes_listsNewestFirstWithHoverAndDeleteSuggestion() {
        server = TestServer.in(folder).start();
        final LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 12; i++) {
            server.notes.save(new Note(null, null, "Steve", "Mod", "note number " + i + " with a long text that is cut in the list",
                    now.minusMinutes(20 - i)));
        }

        server.command(server.console, "notes Steve");

        assertTrue(server.console.received("Staff notes about Steve (12 in total, page 1/2)"), server.console.messages().toString());
        assertTrue(server.console.received("note number 12 with a long text that is ..."));
        assertFalse(server.console.received("note number 2 with"), "page 2");
        assertTrue(server.console.hovers().stream().anyMatch(h -> h.contains("note number 12 with a long text that is cut in the list")));
        assertTrue(server.console.clicks().contains("/notes Steve 2"));
        assertTrue(server.console.suggestions().stream().anyMatch(c -> c.startsWith("/delnote ")));
    }

    @Test
    void delnote_deletesAndUnknownIdIsReported() {
        server = TestServer.in(folder).start();
        final Note note = server.notes.save(Note.create(null, "Steve", "Mod", "x"));

        server.command(server.console, "delnote " + note.getId());
        server.command(server.console, "delnote 999");
        server.command(server.console, "notes Steve");

        assertTrue(server.console.received("Note #" + note.getId() + " of Steve deleted."));
        assertTrue(server.console.received("No note was found with the provided ID."));
        assertTrue(server.console.received("Steve has no staff notes."));
    }

    @Test
    void joining_warnsStaffWithNotesNotify() {
        server = TestServer.in(folder).start();
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.notes.notify"));
        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.3"));
        server.notes.save(Note.create(null, "Steve", "Mod", "x"));
        server.notes.save(Note.create(null, "Steve", "Mod", "y"));

        server.online(new FakePlayer("Steve", "10.0.0.1", "judicator.notes.notify"));
        server.online(new FakePlayer("Clean", "10.0.0.4"));

        assertTrue(staff.received("[Notes] Steve joined and has 2 staff note(s)."), staff.messages().toString());
        assertEquals(1, staff.messages().stream().filter(m -> m.startsWith("[Notes]")).count(), "nothing for a player without notes");
        assertFalse(other.received("[Notes]"));
        assertTrue(server.platform.getPlayer("Steve").map(p -> !((FakePlayer) p).received("[Notes]")).orElseThrow(),
                "not told about their own notes");
    }

    @Test
    void joining_withNotifyOff_saysNothing() {
        server = TestServer.in(folder).set("notes.notify-on-join", false).start();
        final FakePlayer staff = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.notes.notify"));
        server.notes.save(Note.create(null, "Steve", "Mod", "x"));

        server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertFalse(staff.received("[Notes]"));
    }

    @Test
    void notesCommands_needTheirPermissions() {
        server = TestServer.in(folder).start();
        final FakePlayer reader = server.online(new FakePlayer("Reader", "10.0.0.2", "judicator.notes"));

        assertFalse(server.command(reader, "note Steve x"));
        assertFalse(server.command(reader, "delnote 1"));
        assertTrue(server.command(reader, "notes Steve"));
    }

    @Test
    void playerInfo_summarizesThePlayer() {
        server = TestServer.in(folder).set("log-kicks", true).start();
        final FakePlayer viewer = server.online(new FakePlayer("Viewer", "10.0.0.9", "judicator.info"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.command(server.console, "kick Steve calm");
        server.command(server.console, "tempmute Steve 1:d spam");
        server.command(server.console, "warn Steve caps");
        server.command(server.console, "note Steve watch chat");
        server.command(server.console, "ban Steve mistake");
        server.command(server.console, "unban Steve");
        final long muteId = server.punishments.findAllByUsername("Steve").stream()
                .filter(p -> p.isMuteType()).findFirst().orElseThrow().getId();

        server.command(viewer, "playerinfo Steve");

        assertTrue(viewer.received("Information about Steve (online)"), viewer.messages().toString());
        assertTrue(viewer.received("Ban: none"));
        assertTrue(viewer.received("Mute: #" + muteId + " TEMPMUTE until"));
        assertTrue(viewer.received("History: 1 ban(s), 1 mute(s), 1 kick(s)"));
        assertTrue(viewer.received("Warns: 1 active of 1"));
        assertTrue(viewer.received("Staff notes: 1"));
        assertTrue(viewer.received("Accounts that shared an IP: 1"));
        assertTrue(viewer.received("Last login: now"));
        assertFalse(viewer.received("Last IP"), "the IP only for who may see histories");
        assertTrue(viewer.clicks().containsAll(List.of("/pview " + muteId, "/phistory Steve", "/whistory Steve",
                "/notes Steve", "/alts Steve")), viewer.clicks().toString());
    }

    @Test
    void playerInfo_showsTheLastIpToWhoMaySeeHistories() {
        server = TestServer.in(folder).start();
        final FakePlayer viewer = server.online(new FakePlayer("Viewer", "10.0.0.9", "judicator.info", "judicator.history"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.quit(steve);

        server.command(viewer, "playerinfo Steve");

        assertTrue(viewer.received("(offline)"));
        assertTrue(viewer.received("Last IP: 10.0.0.1"), viewer.messages().toString());
        assertFalse(viewer.received("Last login: now"));
    }

    @Test
    void playerInfo_showsTheMuteThatWeighsMost() {
        server = TestServer.in(folder).start();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve forever");
        server.command(server.console, "tempmute Steve 1:h short");
        final long permanentId = server.punishments.findAllByUsername("Steve").stream()
                .filter(p -> p.getType() == com.openmc.judicator.punish.types.PunishType.MUTE).findFirst().orElseThrow().getId();

        server.command(server.console, "playerinfo Steve");

        assertTrue(server.console.received("Mute: #" + permanentId + " MUTE"), server.console.messages().toString());
    }

    @Test
    void playerInfo_unknownPlayer() {
        server = TestServer.in(folder).start();

        server.command(server.console, "playerinfo Nobody");

        assertTrue(server.console.received("Nothing is known about Nobody."));
        assertTrue(server.command(server.console, "pinfo Nobody"), "alias");
    }
}
