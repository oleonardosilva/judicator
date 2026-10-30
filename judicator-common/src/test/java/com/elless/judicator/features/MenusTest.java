package com.elless.judicator.features;

import com.elless.judicator.menus.DialogLayout;
import com.elless.judicator.menus.Menu;
import com.elless.judicator.menus.MenuIcon;
import com.elless.judicator.notes.Note;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.types.PunishStatus;
import com.elless.judicator.punish.types.PunishType;
import com.elless.judicator.reports.ReportState;
import com.elless.judicator.testing.FakeMenus;
import com.elless.judicator.testing.FakePlayer;
import com.elless.judicator.testing.TestServer;
import com.elless.judicator.warns.types.WarnStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    void asDialogs_theWayOutIsApartFromTheActions() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");

        server.command(admin, "jmenu");
        assertEquals(MenuIcon.CLOSE, dialog(admin).exit().item().icon(), "the first screen closes");
        menus.click(admin, "Bans in force");
        menus.click(admin, "#1");
        final DialogLayout punishment = dialog(admin);
        assertEquals(MenuIcon.BACK, punishment.exit().item().icon());
        assertTrue(punishment.buttons().stream().map(button -> button.item().icon())
                .noneMatch(icon -> icon == MenuIcon.BACK || icon == MenuIcon.CLOSE));
        assertTrue(punishment.body().stream().anyMatch(part -> part instanceof DialogLayout.Content content
                && content.item().name().replaceAll("§.", "").startsWith("#1")), "what the screen is about, on top");

        menus.click(admin, "Revoke");
        final DialogLayout confirm = dialog(admin);
        assertEquals("Cancel", confirm.exit().item().name().replaceAll("§.", ""));
        assertEquals(List.of("Confirm", "Confirm with a reason"), confirm.buttons().stream()
                .map(button -> button.item().name().replaceAll("§.", "")).toList());

        server.command(admin, "pview 1");
        assertEquals(MenuIcon.CLOSE, dialog(admin).exit().item().icon(), "opened by a command: nothing to go back to");
    }

    @Test
    void playerPage_navigationApartFromTheActions() {
        start();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));

        server.command(admin, "jmenu Steve");
        final Menu menu = menus.of(admin).orElseThrow();
        assertEquals(MenuIcon.BACK, menu.item(45).orElseThrow().icon(), "bottom left, as on the lists");
        assertEquals(MenuIcon.CLOSE, menu.item(53).orElseThrow().icon(), "bottom right");
        assertTrue(menu.items().keySet().stream().filter(slot -> slot >= 45).allMatch(slot -> slot == 45 || slot == 53),
                "nothing else on the navigation row");
        final DialogLayout dialog = dialog(admin);
        assertEquals(MenuIcon.CLOSE, dialog.exit().item().icon(), "opened by the command: nothing to go back to");
        assertTrue(dialog.buttons().stream().noneMatch(button -> button.item().icon() == MenuIcon.BACK));
    }

    private DialogLayout dialog(FakePlayer viewer) {
        return DialogLayout.of(menus.of(viewer).orElseThrow());
    }

    @Test
    void lists_pageTwentyEightAtATime_withAMarginAroundThem() {
        start();
        final FakePlayer admin = admin();
        for (int i = 1; i <= 50; i++) server.command(server.console, "ban Player" + i + " spam");

        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        assertEquals(28, menus.names(admin).stream().filter(name -> name.startsWith("#")).count());
        final List<Integer> slots = menus.of(admin).orElseThrow().items().entrySet().stream()
                .filter(entry -> entry.getValue().name().replaceAll("§.", "").startsWith("#")).map(Map.Entry::getKey).toList();
        assertEquals(10, slots.get(0), "the first entry leaves the top row and the left column empty");
        assertEquals(43, slots.get(slots.size() - 1));
        assertTrue(slots.stream().noneMatch(slot -> slot % 9 == 0 || slot % 9 == 8), "no entry on the side columns");
        assertTrue(menus.item(admin, "Page 1/2").isPresent());
        assertFalse(menus.item(admin, "Previous page").isPresent());

        menus.click(admin, "Next page");
        assertEquals(22, menus.names(admin).stream().filter(name -> name.startsWith("#")).count());
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
        assertEquals("diamond_sword", menus.item(mod, "Cheating").orElseThrow().material(), "icon: of the reason");
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
        menus.click(admin, "Accounts linked by IP");
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

        menus.click(admin, "Close the report");
        assertEquals("Confirm", menus.title(admin));
        menus.click(admin, "Confirm with a reason");
        server.chat(admin, "warned him");
        assertEquals(ReportState.CLOSED, server.judicator.getReportService().findById(1).orElseThrow().getState());
        assertEquals("warned him", server.judicator.getReportService().findById(1).orElseThrow().getCloseComment().orElseThrow());

        menus.click(admin, "Back");
        assertEquals("Reports: open (0)", menus.title(admin));
        menus.click(admin, "Show: open");
        assertEquals("Reports: assigned (0)", menus.title(admin));
        menus.click(admin, "Show: assigned");
        assertEquals("Reports: closed (1)", menus.title(admin));
    }

    @Test
    void listsAreSortedFilteredAndSearched() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Charlie griefing");
        server.command(server.console, "tempban Alice 2:d spam");
        server.command(server.console, "tempban Bob 9:d xray");
        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        assertEquals(List.of("Bob", "Alice", "Charlie"), players(admin), "newest first");
        assertEquals(List.of("» newest", "  oldest", "  ending soonest", "  name", "", "Click for: oldest"),
                menus.lore(admin, "Sort: newest"));

        menus.click(admin, "Sort: newest");
        assertEquals(List.of("Charlie", "Alice", "Bob"), players(admin));
        menus.click(admin, "Sort: oldest");
        assertEquals(List.of("Alice", "Bob", "Charlie"), players(admin), "ending soonest, permanent last");
        menus.click(admin, "Sort: ending soonest");
        assertEquals(List.of("Alice", "Bob", "Charlie"), players(admin), "by name");

        menus.click(admin, "Show: all");
        assertEquals(List.of("Charlie"), players(admin), "permanent");
        menus.click(admin, "Show: permanent");
        assertEquals(List.of("Alice", "Bob"), players(admin), "temporary");
        assertEquals(List.of("2 found.", "Click to clear the filters."), menus.lore(admin, "Page 1/1"));

        menus.click(admin, "Page 1/1");
        assertEquals(List.of("Bob", "Alice", "Charlie"), players(admin), "back to the defaults");
        menus.click(admin, "Back");
        assertEquals("Judicator", menus.title(admin), "the options replace the list: back leaves it");
    }

    @Test
    void inventoriesAskTheSearchInTheChat() {
        start();
        final FakePlayer admin = admin();
        server.command(server.console, "tempban Alice 2:d spam");
        server.command(server.console, "tempban Bob 9:d xray in the mine");
        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");

        menus.click(admin, "Search");
        assertTrue(admin.received("Type a name or a word in the chat, or cancel to go back."));
        server.chat(admin, "XRAY");

        assertEquals(List.of("Bob"), players(admin), "the reason, ignoring case");
        assertTrue(menus.item(admin, "Search: XRAY").isPresent());
        menus.click(admin, "Back");
        assertEquals("Judicator", menus.title(admin));
    }

    @Test
    void dialogsSetTheOrderFiltersAndSearchInOneForm() {
        start();
        menus.dialogs();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "tempban Steve 1:h first");
        server.command(server.console, "unban Steve");
        server.command(server.console, "mute Steve second");
        server.command(server.console, "warn Steve third");
        server.command(admin, "jmenu Steve");
        menus.click(admin, "Punishment history");

        menus.click(admin, "Search");
        assertEquals(List.of("order", "filter", "status", "search"), menus.formFields(admin));
        menus.submit(admin, Map.of("order", "oldest", "status", "revoked"));

        assertEquals(List.of("#1 - TEMPBAN - Revoked"), menus.names(admin).stream().filter(name -> name.startsWith("#")).toList());
        assertTrue(menus.item(admin, "Status: revoked").isPresent());
        assertTrue(menus.item(admin, "Sort: oldest").isPresent());
        menus.click(admin, "Back");
        assertEquals("Steve", menus.title(admin), "the form replaced the list instead of stacking it");
    }

    @Test
    void otherListsHaveTheirOwnOptions() {
        start();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.online(new FakePlayer("SteveAlt", "10.0.0.1"));
        server.online(new FakePlayer("Clean", "10.0.0.1"));
        server.command(server.console, "ban SteveAlt evading");
        server.command(server.console, "warn Steve one");
        server.command(server.console, "warn Steve two");
        server.command(server.console, "unwarn 1");
        server.command(admin, "jmenu Steve");

        menus.click(admin, "Accounts linked by IP");
        menus.click(admin, "Show: all");
        assertEquals(List.of("SteveAlt"), menus.names(admin).stream().filter(name -> name.startsWith("Steve") || name.equals("Clean")).toList());
        menus.click(admin, "Back");

        menus.click(admin, "Warns");
        assertFalse(menus.item(admin, "Show:").isPresent(), "warns have no kind filter");
        menus.click(admin, "Status: all");
        assertEquals(List.of("#2 - Active"), menus.names(admin).stream().filter(name -> name.startsWith("#")).toList());
    }

    private List<String> players(FakePlayer viewer) {
        return menus.of(viewer).orElseThrow().items().entrySet().stream()
                .filter(entry -> entry.getKey() < 45)
                .map(entry -> entry.getValue().lore().get(0).replaceAll("§.", "").replace("Player: ", ""))
                .toList();
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
    void theListCommandsOpenTheMenuForPlayers() {
        start();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        for (int i = 1; i <= 50; i++) server.command(server.console, "ban Player" + i + " spam");
        server.command(server.console, "mute Steve spam");
        server.command(server.console, "note Steve watch him");

        server.command(admin, "banlist");
        assertEquals("Bans in force (50)", menus.title(admin));
        assertFalse(admin.received("Bans in force"), "no chat answer");
        server.command(admin, "banlist 2");
        assertTrue(menus.item(admin, "Page 2/2").isPresent());
        server.command(admin, "mutelist");
        assertEquals("Mutes in force (1)", menus.title(admin));
        server.command(admin, "phistory Steve");
        assertEquals("History of Steve", menus.title(admin));
        server.command(admin, "whistory Steve");
        assertEquals("Warns of Steve", menus.title(admin));
        server.command(admin, "notes Steve 1");
        assertEquals("Notes about Steve", menus.title(admin));
        server.command(admin, "alts Steve");
        assertTrue(menus.title(admin).startsWith("Accounts of"));
        server.command(admin, "pinfo Steve");
        assertEquals("Steve", menus.title(admin));
        server.command(admin, "pview 1");
        assertEquals("Punishment #1", menus.title(admin));
        server.command(admin, "punish Steve");
        assertEquals("Punish Steve", menus.title(admin));
        server.command(admin, "reports");
        assertEquals("Reports: open (0)", menus.title(admin));
        server.command(admin, "reports list closed");
        assertEquals("Reports: closed (0)", menus.title(admin));
        server.command(admin, "blame Console");
        assertEquals("Applied by Console", menus.title(admin));

        server.command(admin, "blame Console 7:d");
        assertTrue(admin.received("Punishments applied by Console"), "a period keeps the chat answer");
        server.command(server.console, "banlist");
        assertTrue(server.console.received("Bans in force"), "the console keeps the chat");
    }

    @Test
    void replaceCommandsOffKeepsTheChat() {
        start(TestServer.in(folder).set("menus.replace-commands", false));
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");

        server.command(admin, "banlist");

        assertTrue(admin.received("Bans in force"));
        assertNull(menus.title(admin));
    }

    @Test
    void entriesShowThePlayersHead() {
        start();
        final FakePlayer admin = admin();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(server.console, "ban Steve cheating");
        server.command(server.console, "ban Notch griefing");

        server.command(admin, "banlist");

        final List<com.elless.judicator.menus.MenuItem> entries = menus.of(admin).orElseThrow().items().entrySet().stream()
                .filter(entry -> entry.getKey() < 45).map(java.util.Map.Entry::getValue).toList();
        assertTrue(entries.stream().allMatch(item -> item.icon() == com.elless.judicator.menus.MenuIcon.PLAYER_HEAD));
        assertEquals(List.of("Notch", "Steve"), entries.stream().map(com.elless.judicator.menus.MenuItem::headName).toList());
        assertEquals(steve.getUniqueId(), entries.get(1).head());
    }

    @Test
    void staffWithPunishmentsHaveTheirListOnTheirPage() {
        start();
        final FakePlayer admin = admin();
        final FakePlayer mod = server.online(new FakePlayer("Mod", "10.0.0.3", "judicator.kick"));
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(mod, "kick Steve go");
        server.command(server.console, "ban Alex spam");

        server.command(admin, "jmenu Console");
        menus.click(admin, "Punishments applied");
        assertEquals("Applied by Console", menus.title(admin));
        assertEquals(1, menus.names(admin).stream().filter(name -> name.startsWith("#")).count());

        server.command(admin, "jmenu Steve");
        assertFalse(menus.item(admin, "Punishments applied").isPresent(), "nothing applied");
    }

    @Test
    void aDoubleClickRunsTheActionOnce() {
        start();
        final FakePlayer admin = admin();
        final FakePlayer steve = server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(admin, "jmenu Steve");
        menus.click(admin, "Punish");
        menus.click(admin, "Cheating");

        server.platform.holdAsync();
        menus.click(admin, "Apply now");
        menus.click(admin, "Apply now");
        menus.click(admin, "Cancel");
        server.platform.releaseAsync();

        assertEquals(1, server.judicator.getPunishService().findAllOf(server.judicator.getPlayerDirectory().refOf(steve)).size());
        assertEquals("Steve", menus.title(admin), "back on the player once, not twice");
    }

    @Test
    void aMenuClosedWhileTheNextOneIsBuiltStaysClosed() {
        start();
        final FakePlayer admin = admin();
        server.command(admin, "jmenu");

        server.platform.holdAsync();
        menus.click(admin, "Bans in force");
        menus.escape(admin);
        server.platform.releaseAsync();

        assertNull(menus.title(admin));
        assertFalse(server.judicator.getMenuService().hasOpen(admin.getUniqueId()));
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
    void dialogsAskOnTheirOwnScreenInsteadOfTheChat() {
        start();
        menus.dialogs();
        final FakePlayer admin = admin();
        server.command(server.console, "ban Steve cheating");
        server.command(admin, "jmenu");
        menus.click(admin, "Bans in force");
        menus.click(admin, "#1");
        menus.click(admin, "Revoke");

        menus.click(admin, "Confirm with a reason");
        assertEquals("Type the reason:", menus.question(admin));
        assertFalse(admin.received("in the chat"), "nothing asked in the chat");
        assertEquals(TestServer.Outcome.ALLOWED, server.chat(admin, "hello"), "the chat is free");

        menus.answer(admin, "appeal accepted");
        assertEquals("appeal accepted", punishment(1).getRevokedReason().orElseThrow());
        assertEquals("Punishment #1", menus.title(admin));
    }

    @Test
    void dialogs_cancellingTheQuestionGoesBack() {
        start();
        menus.dialogs();
        final FakePlayer admin = admin();
        server.online(new FakePlayer("Steve", "10.0.0.1"));
        server.command(admin, "jmenu Steve");

        menus.click(admin, "Write a note");
        menus.cancelQuestion(admin);

        assertTrue(server.notes.all().isEmpty());
        assertEquals("Steve", menus.title(admin));
    }

    @Test
    void eachMenuKnowsWhichScreenItIs() {
        start();
        final FakePlayer admin = admin();
        server.command(admin, "jmenu");
        assertEquals("main", menus.of(admin).orElseThrow().key());
        assertTrue(menus.of(admin).orElseThrow().header().isEmpty(), "no header unless the messages give one");
        menus.click(admin, "Bans in force");
        assertEquals("punishments", menus.of(admin).orElseThrow().key());
    }

    @Test
    void aHeaderFromTheMessagesFile() throws Exception {
        // what a server's resource pack would use: a private-use character its font draws as a banner
        final String bundled = new String(MenusTest.class.getResourceAsStream("/messages_en.yml").readAllBytes(), StandardCharsets.UTF_8);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("messages_en.yml"),
                bundled + "\n  style:\n    main:\n      header:\n        - \"\\uE001 &6Judicator\"\n", StandardCharsets.UTF_8);
        start();
        final FakePlayer admin = admin();

        server.command(admin, "jmenu");

        assertEquals(List.of("\uE001 §6Judicator"), menus.of(admin).orElseThrow().header());
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
