package com.openmc.judicator.features;

import com.openmc.judicator.menus.Menu;
import com.openmc.judicator.notes.Note;
import com.openmc.judicator.platform.PlatformType;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.types.PunishStatus;
import com.openmc.judicator.punish.types.PunishType;
import com.openmc.judicator.reports.ReportState;
import com.openmc.judicator.testing.FakeMenus;
import com.openmc.judicator.testing.FakePlayer;
import com.openmc.judicator.testing.TestServer;
import com.openmc.judicator.warns.types.WarnStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menus of /jmenu: what each screen shows to whom, the navigation between them and the actions, which go through
 * the same commands (permissions, staff rules) as typing them.
 */
class MenusTest {

    @TempDir
    Path folder;

    private TestServer server;
    private FakeMenus menus;

    @AfterEach
    void stop() {
        if (server != null) server.close();
    }

    private void start() {
        start(TestServer.in(folder));
    }

    private void start(TestServer.Builder builder) {
        server = builder.platform(PlatformType.BUKKIT).start();
        menus = server.platform.menus;
    }

    private FakePlayer admin() {
        return server.online(new FakePlayer("Admin", "10.0.0.9", "judicator.admin"));
    }

    private Punishment punishment(long id) {
        return server.judicator.getPunishService().findById(id).orElseThrow();
    }

    @Test
    void jmenu_isForPlayersWithThePermission() {
        start();
        final FakePlayer player = server.online(new FakePlayer("Steve", "10.0.0.1"));

        assertFalse(server.command(player, "jmenu"), "hidden without judicator.menu");
        server.command(server.console, "jmenu");

        assertTrue(server.console.received("Menus are for players in the game."));
        assertNull(menus.title(player));
    }

    @Test
    void proxiesHaveNoMenus() {
        server = TestServer.in(folder).start();

        assertFalse(server.judicator.getMenuService().isAvailable());
        assertTrue(server.judicator.getCommands().stream().noneMatch(command -> command.getName().equals("jmenu")));
    }

    @Test
    void mainMenu_showsOnlyTheListsTheStaffMemberMayUse() {
        start();
        final FakePlayer helper = server.online(new FakePlayer("Helper", "10.0.0.2", "judicator.menu"));
        server.command(server.console, "ban Steve cheating");

        server.command(helper, "jmenu");

        assertEquals("Judicator", menus.title(helper));
        assertFalse(menus.item(helper, "Bans in force").isPresent());
        assertTrue(menus.item(helper, "Players online").isPresent());
        assertTrue(menus.item(helper, "Find a player").isPresent());

        final FakePlayer admin = admin();
        server.command(admin, "jmenu");
        assertEquals(List.of("1 in force.", "", "Click to see them."), menus.lore(admin, "Bans in force"));
        assertTrue(menus.item(admin, "Mutes in force").isPresent());
        assertTrue(menus.item(admin, "Reports").isPresent());
    }

    @Test
    void navigation_goesIntoAListAndAPunishmentAndBack() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");

        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        assertEquals("Bans in force (1)", menus.title(admin));
        assertTrue(menus.lore(admin, "#1").contains("Reason: cheating"));

        menus.click(admin, "#1");
        assertEquals("Punishment #1", menus.title(admin));
        assertTrue(menus.lore(admin, "#1").contains("Player: Steve"));

        menus.click(admin, "Back");
        assertEquals("Bans in force (1)", menus.title(admin));
        menus.click(admin, "Back");
        assertEquals("Judicator", menus.title(admin));
        menus.click(admin, "Close");
        assertNull(menus.title(admin));
        assertFalse(server.judicator.getMenuService().hasOpen(admin.getUniqueId()));
    }

    @Test
    void lists_pageFortyFiveAtATime() {
        start();
        final FakePlayer admin = admin();
        for (int i = 1; i <= 50; i++) server.command(server.console, "ban Player" + i + " spam");

        server.command(admin, "banlist");
        assertTrue(admin.received("Bans in force"), "chat answer: menus.replace-commands is off by default");
        assertNull(menus.title(admin));

        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        assertEquals(45, menus.names(admin).stream().filter(name -> name.startsWith("#")).count());
        assertTrue(menus.item(admin, "Page 1/2").isPresent());
        assertFalse(menus.item(admin, "Previous page").isPresent());

        menus.click(admin, "Next page");
        assertEquals(5, menus.names(admin).stream().filter(name -> name.startsWith("#")).count());
        assertTrue(menus.item(admin, "Page 2/2").isPresent());
        assertFalse(menus.item(admin, "Next page").isPresent());

        menus.click(admin, "Back");
        assertEquals("Judicator", menus.title(admin), "a page replaces the previous one: back leaves the list");
    }

    @Test
    void playerScreen_summarisesThePlayerAndShowsTheIpOnlyWithHistories() {
        start();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempmute Steve 1:d spam");
        server.command(server.console, "warn Steve language");
        final FakePlayer viewer = server.online(new FakePlayer("Viewer", "10.0.0.3", "judicator.menu", "judicator.info"));

        server.command(viewer, "jmenu Steve");

        assertEquals("Steve", menus.title(viewer));
        final List<String> head = menus.lore(viewer, "Steve");
        assertTrue(head.contains("UUID: " + steve.getUniqueId()), head.toString());
        assertTrue(head.contains("Warns: 1 active of 1"), head.toString());
        assertFalse(head.stream().anyMatch(line -> line.startsWith("Last IP")));
        assertTrue(menus.item(viewer, "Muted: #1 TEMPMUTE").isPresent());
        assertTrue(menus.item(viewer, "Not banned").isPresent());
        assertFalse(menus.item(viewer, "Punishment history").isPresent(), "needs judicator.history");
        assertFalse(menus.item(viewer, "Unmute").isPresent(), "needs judicator.unmute");

        viewer.grant("judicator.history");
        menus.click(viewer, "Close");
        server.command(viewer, "jmenu Steve");
        assertTrue(menus.lore(viewer, "Steve").contains("Last IP: 10.0.0.1"));
        menus.click(viewer, "Punishment history");
        assertEquals("History of Steve", menus.title(viewer));
    }

    @Test
    void revoke_asksForConfirmationAndReturnsToThePunishment() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");

        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        menus.click(admin, "#1");
        menus.click(admin, "Revoke");
        assertEquals("Confirm", menus.title(admin));
        assertTrue(menus.item(admin, "Revoke punishment #1?").isPresent());

        menus.click(admin, "Cancel");
        assertEquals("Punishment #1", menus.title(admin));
        assertFalse(punishment(1).isRevoked());

        menus.click(admin, "Revoke");
        menus.click(admin, "Confirm");

        assertTrue(punishment(1).isRevoked());
        assertTrue(admin.received("The punishment with the provided ID has been successfully revoked."));
        assertEquals("Punishment #1", menus.title(admin));
        assertFalse(menus.names(admin).contains("Revoke"), "already revoked");
        menus.click(admin, "Back");
        assertEquals("Bans in force (0)", menus.title(admin));
    }

    @Test
    void revokeWithAReason_asksInTheChatAndComesBack() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");
        server.command(admin, "pview 1");
        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        menus.click(admin, "#1");
        menus.click(admin, "Revoke");

        menus.click(admin, "Confirm with a reason");
        assertNull(menus.title(admin), "the menu closes for the chat");
        assertTrue(admin.received("Type the reason in the chat, or cancel to go back."));

        assertEquals(TestServer.Outcome.TAKEN_BY_PROMPT, server.chat(admin, "appeal accepted"));

        assertTrue(punishment(1).isRevoked());
        assertEquals("appeal accepted", punishment(1).getRevokedReason().orElseThrow());
        assertEquals("Punishment #1", menus.title(admin), "back where the button was, past the confirmation");
    }

    @Test
    void prompt_cancelWordBringsTheMenuBackWithoutRunningAnything() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");
        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        menus.click(admin, "#1");
        menus.click(admin, "Revoke");
        menus.click(admin, "Confirm with a reason");

        server.chat(admin, "cancel");

        assertFalse(punishment(1).isRevoked());
        assertEquals("Confirm", menus.title(admin));
        assertEquals(TestServer.Outcome.ALLOWED, server.chat(admin, "hello"), "the question is over");
    }

    @Test
    void punish_appliesTheReasonWithoutTheEvidencePromptAfterConfirming() {
        start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.menu", "judicator.punish"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(mod, "jmenu Steve");
        menus.click(mod, "Punish");
        assertEquals("Punish Steve", menus.title(mod));
        assertTrue(menus.lore(mod, "Player Offense").contains("Gives: TEMPMUTE"));
        menus.click(mod, "Cheating");
        assertEquals(List.of("Reason: Cheating", "Gives: TEMPBAN", "Duration: 7 months, 2 weeks", "By IP: No"),
                menus.lore(mod, "Punish Steve?"));
        assertFalse(menus.item(mod, "Silent").isPresent(), "needs judicator.silent");

        menus.click(mod, "Apply now");

        final Punishment applied = server.judicator.getPunishService().findAllOf(server.judicator.getPlayerDirectory().refOf(steve)).get(0);
        assertEquals(PunishType.TEMPBAN, applied.getType());
        assertEquals("Mod", applied.getPunisher());
        assertTrue(steve.wasKicked());
        assertTrue(server.judicator.getPunishCache().getContext("Mod").isEmpty(), "no evidence prompt");
        assertEquals("Steve", menus.title(mod), "back on the player");
    }

    @Test
    void punishWithEvidence_goesThroughTheChatPrompt() {
        start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.menu", "judicator.punish"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "jmenu Steve");
        menus.click(mod, "Punish");
        menus.click(mod, "Cheating");

        menus.click(mod, "Add evidence first");
        assertNull(menus.title(mod));
        assertFalse(steve.wasKicked());

        server.chat(mod, "https://example.org/clip");
        server.chat(mod, "confirm");

        assertTrue(steve.wasKicked());
        final Punishment applied = server.judicator.getPunishService().findAllOf(server.judicator.getPlayerDirectory().refOf(steve)).get(0);
        assertEquals(List.of("https://example.org/clip"), applied.getEvidences());
    }

    @Test
    void punish_silentToggleNeedsThePermission() {
        start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.menu", "judicator.punish", "judicator.silent"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "jmenu Steve");
        menus.click(mod, "Punish");
        menus.click(mod, "Cheating");

        menus.click(mod, "Silent: no");
        assertTrue(menus.item(mod, "Silent: yes").isPresent());
        menus.click(mod, "Apply now");

        assertTrue(steve.wasKicked());
        assertTrue(server.platform.announcements().isEmpty(), "silent: not announced");
    }

    @Test
    void actionsKeepTheStaffRules() {
        start(TestServer.in(folder).immunePlayers("  Steve: all\n"));
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.menu", "judicator.punish", "judicator.kick"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "jmenu Steve");

        menus.click(mod, "Kick");
        menus.click(mod, "Confirm");
        menus.click(mod, "Punish");
        menus.click(mod, "Cheating");
        menus.click(mod, "Apply now");

        assertFalse(steve.wasKicked());
        assertTrue(server.judicator.getPunishService().findAllOf(server.judicator.getPlayerDirectory().refOf(steve)).isEmpty());
    }

    @Test
    void aButtonIsCheckedAgainWhenClicked() {
        start();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.2", "judicator.menu", "judicator.kick"));
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "jmenu Steve");
        menus.click(mod, "Kick");

        mod.deny("judicator.kick");
        menus.click(mod, "Confirm");

        assertFalse(steve.wasKicked());
        assertTrue(mod.received("You do not have permission to do this."));
    }

    @Test
    void kickUnbanAndUnmuteFromThePlayer() {
        start();
        final FakePlayer admin = admin();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "mute Steve spam");

        server.command(admin, "jmenu Steve");
        menus.click(admin, "Unmute");
        menus.click(admin, "Confirm");
        assertEquals(PunishStatus.REVOKED, punishment(1).getStatus());
        assertEquals("Steve", menus.title(admin));
        assertTrue(menus.item(admin, "Not muted").isPresent());

        menus.click(admin, "Kick");
        menus.click(admin, "Confirm");
        assertTrue(steve.wasKicked());

        server.quit(steve);
        server.command(server.console, "ban Steve cheating");
        menus.click(admin, "Close");
        server.command(admin, "jmenu Steve");
        assertFalse(menus.item(admin, "Kick").isPresent(), "offline");
        menus.click(admin, "Unban");
        menus.click(admin, "Confirm");
        assertTrue(punishment(2).isRevoked());
    }

    @Test
    void warnAndNoteAreTypedInTheChat() {
        start();
        final FakePlayer admin = admin();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(admin, "jmenu Steve");

        menus.click(admin, "Warn");
        assertTrue(admin.received("Type the reason of the warn in the chat, or cancel to go back."));
        server.chat(admin, "bad language");
        assertEquals("bad language", server.judicator.getWarnService().findAllOf(server.judicator.getPlayerDirectory().refOf(steve)).get(0).getReason());
        assertEquals("Steve", menus.title(admin));

        menus.click(admin, "Write a note");
        server.chat(admin, "watch the fly hacks");
        assertEquals("watch the fly hacks", server.notes.all().get(0).getContent());
        assertTrue(menus.lore(admin, "Staff notes").contains("1 note(s)."));
    }

    @Test
    void warnAndNoteScreensRemoveAndDelete() {
        start();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "warn Steve language");
        server.notes.save(new Note(null, null, "Steve", "Mod", "a note that is long enough to be wrapped in two lines of lore", LocalDateTime.now()));

        server.command(admin, "jmenu Steve");
        menus.click(admin, "Warns");
        assertEquals("Warns of Steve", menus.title(admin));
        menus.click(admin, "#1");
        menus.click(admin, "Remove the warn");
        menus.click(admin, "Confirm");
        assertTrue(server.judicator.getWarnService().findById(1L).map(warn -> warn.getStatus() != WarnStatus.ACTIVE).orElse(true));
        menus.click(admin, "Back");
        menus.click(admin, "Back");

        menus.click(admin, "Staff notes");
        final List<String> lore = menus.lore(admin, "#1 by Mod");
        assertTrue(lore.contains("a note that is long enough to be"), lore.toString());
        assertTrue(lore.contains("wrapped in two lines of lore"), lore.toString());
        menus.click(admin, "#1 by Mod");
        menus.click(admin, "Confirm");
        assertTrue(server.notes.all().isEmpty());
        assertEquals("Notes about Steve", menus.title(admin));
    }

    @Test
    void altsOpenTheOtherAccount() {
        start();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        final FakePlayer alt = server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.quit(alt);
        server.command(server.console, "ban SteveAlt evading");

        server.command(admin, "jmenu Steve");
        menus.click(admin, "Accounts that shared an IP");
        assertEquals(List.of("banned", "", "Click to open."), menus.lore(admin, "SteveAlt"));
        menus.click(admin, "SteveAlt");
        assertEquals("SteveAlt", menus.title(admin));
    }

    @Test
    void reports_filterAssignAndClose() {
        start();
        final FakePlayer admin = admin();
        final FakePlayer reporter = server.online(new FakePlayer("Alex", "10.0.0.4"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(reporter, "report Steve flying");

        server.command(admin, "jmenu");
        menus.click(admin, "Reports");
        assertEquals("Reports: open (1)", menus.title(admin));
        menus.click(admin, "#1");
        menus.click(admin, "Assign to me");
        assertEquals(ReportState.ASSIGNED, server.judicator.getReportService().findById(1).orElseThrow().getState());
        assertTrue(menus.item(admin, "Open it again").isPresent());

        menus.click(admin, "Close");
        assertEquals("Confirm", menus.title(admin));
        menus.click(admin, "Confirm with a reason");
        server.chat(admin, "warned him");
        assertEquals(ReportState.CLOSED, server.judicator.getReportService().findById(1).orElseThrow().getState());
        assertEquals("warned him", server.judicator.getReportService().findById(1).orElseThrow().getCloseComment().orElseThrow());

        menus.click(admin, "Back");
        assertEquals("Reports: open (0)", menus.title(admin));
        menus.click(admin, "Showing: open");
        assertEquals("Reports: assigned (0)", menus.title(admin));
        menus.click(admin, "Showing: assigned");
        assertEquals("Reports: closed (1)", menus.title(admin));
    }

    @Test
    void search_typesANameInTheChat() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Notch griefing");
        server.command(admin, "jmenu");

        menus.click(admin, "Find a player");
        server.chat(admin, "Notch");

        assertEquals("Notch", menus.title(admin));
        assertTrue(menus.item(admin, "Banned: #1 BAN").isPresent());
        menus.click(admin, "Back");
        assertEquals("Judicator", menus.title(admin));
    }

    @Test
    void replaceCommands_opensTheMenuForPlayersOnly() {
        start(TestServer.in(folder).set("menus.replace-commands", true));
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");

        server.command(admin, "banlist");
        assertEquals("Bans in force (1)", menus.title(admin));
        server.command(admin, "pview 1");
        assertEquals("Punishment #1", menus.title(admin));
        server.command(admin, "punish Steve");
        assertEquals("Punish Steve", menus.title(admin));

        server.command(admin, "banlist 2");
        assertTrue(admin.received("Bans in force"), "a page argument keeps the chat answer");
        server.command(server.console, "banlist");
        assertTrue(server.console.received("Bans in force"));
    }

    @Test
    void aClickOnAMenuThatWasReplacedIsIgnored() {
        start();
        final FakePlayer admin = admin();
        for (int i = 1; i <= 30; i++) server.command(server.console, "ban Player" + i + " spam");
        server.command(admin, "jmenu");
        final Menu main = menus.of(admin).orElseThrow();
        menus.click(admin, "Bans in force");

        // slot 22: "Find a player" in the old menu, a ban in the list now
        server.judicator.getMenuService().click(admin.getUniqueId(), main, 22);

        assertEquals("Bans in force (30)", menus.title(admin));
        assertTrue(server.judicator.getPunishCache().getContext("Admin").isEmpty(), "no search question either");
    }

    @Test
    void sessionsEndWhenTheMenuClosesOrThePlayerLeaves() {
        start();
        final FakePlayer admin = admin();
        server.command(admin, "jmenu");
        menus.escape(admin);
        assertFalse(server.judicator.getMenuService().hasOpen(admin.getUniqueId()));

        server.command(admin, "jmenu");
        server.quit(admin);
        assertFalse(server.judicator.getMenuService().hasOpen(admin.getUniqueId()));

        final FakePlayer other = server.online(new FakePlayer("Other", "10.0.0.5", "judicator.admin"));
        server.command(other, "jmenu");
        server.reloadPlugin();
        assertNull(menus.title(other), "disabling the plugin closes every menu");
    }

    @Test
    void menusSpeakThePlayersLanguage() {
        start(TestServer.in(folder).set("per-player-language", true));
        final FakePlayer admin = server.online(new FakePlayer("Admin", "10.0.0.9", "judicator.admin").locale("pt_BR"));

        server.command(admin, "jmenu");

        assertTrue(menus.item(admin, "Bans em vigor").isPresent(), menus.names(admin).toString());
        assertTrue(menus.item(admin, "Procurar um jogador").isPresent());
    }
}
