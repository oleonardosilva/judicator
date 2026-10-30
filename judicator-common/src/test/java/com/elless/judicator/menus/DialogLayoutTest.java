package com.elless.judicator.menus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How a menu lays out as a dialog: the way out apart from the actions, confirmations, lists.
 */
class DialogLayoutTest {

    private static final Runnable CLICK = () -> { };

    private static MenuItem button(MenuIcon icon, String name) {
        return MenuItem.of(icon, name, List.of()).onClick(CLICK);
    }

    private static List<Integer> slots(DialogLayout layout) {
        return layout.buttons().stream().map(DialogLayout.Button::slot).toList();
    }

    private static Menu screen(boolean previous) {
        return Menu.builder("Punishment #1", 3)
                .set(4, MenuItem.of(MenuIcon.BAN, "#1", List.of("Player: Steve")))
                .set(10, button(MenuIcon.PLAYER_HEAD, "Steve"))
                .set(12, button(MenuIcon.PARDON, "Revoke"))
                .set(18, button(MenuIcon.BACK, "Back"))
                .set(26, button(MenuIcon.CLOSE, "Close"))
                .build()
                .styled("punishment", List.of(), previous);
    }

    @Test
    void back_isTheWayOut_neverAmongTheActions() {
        final DialogLayout layout = DialogLayout.of(screen(true));

        assertEquals(18, layout.exit().slot());
        assertEquals(List.of(10, 12), slots(layout));
        assertNull(layout.confirm());
    }

    @Test
    void firstScreen_closesInstead() {
        final DialogLayout layout = DialogLayout.of(screen(false));

        assertEquals(26, layout.exit().slot());
        assertEquals(List.of(10, 12), slots(layout));
    }

    @Test
    void itemsWithoutAClick_areTheBody_afterTheHeader() {
        final Menu menu = Menu.builder("Steve", 3)
                .set(4, MenuItem.of(MenuIcon.PLAYER_HEAD, "Steve", List.of()))
                .set(9, MenuItem.of(MenuIcon.FILLER, " ", List.of()))
                .set(19, MenuItem.of(MenuIcon.CONFIRM, "Not banned", List.of()))
                .set(26, button(MenuIcon.CLOSE, "Close"))
                .build()
                .styled("player", List.of("banner"), false);

        final List<DialogLayout.Part> body = DialogLayout.of(menu).body();

        assertEquals(new DialogLayout.Line("banner"), body.get(0));
        assertEquals(List.of("Steve", "Not banned"), body.subList(1, body.size()).stream()
                .map(part -> ((DialogLayout.Content) part).item().name()).toList());
    }

    @Test
    void confirmation_yesAndNoSideBySide() {
        final Menu menu = Menu.builder("Confirm", 3)
                .set(4, MenuItem.of(MenuIcon.NOTE, "Delete note #1?", List.of()))
                .set(11, button(MenuIcon.CONFIRM, "Confirm"))
                .set(15, button(MenuIcon.CANCEL, "Cancel"))
                .build()
                .styled("confirm", List.of(), true);

        final DialogLayout layout = DialogLayout.of(menu);

        assertEquals(11, layout.confirm().slot());
        assertEquals(15, layout.exit().slot());
        assertEquals(List.of(), slots(layout));
    }

    @Test
    void confirmationWithMoreChoices_cancelBelowThem() {
        final Menu menu = Menu.builder("Confirm", 3)
                .set(11, button(MenuIcon.CONFIRM, "Apply now"))
                .set(13, button(MenuIcon.EDIT, "Add evidence first"))
                .set(15, button(MenuIcon.CANCEL, "Cancel"))
                .set(22, button(MenuIcon.TOGGLE_OFF, "Silent: no"))
                .build()
                .styled("punishConfirm", List.of(), true);

        final DialogLayout layout = DialogLayout.of(menu);

        assertEquals(15, layout.exit().slot());
        assertEquals(List.of(11, 13, 22), slots(layout));
        assertNull(layout.confirm());
    }

    @Test
    void cancel_isAnAction_whereTheScreenHasBack() {
        final Menu menu = Menu.builder("Report #1", 3)
                .set(13, button(MenuIcon.CANCEL, "Reopen"))
                .set(18, button(MenuIcon.BACK, "Back"))
                .build()
                .styled("report", List.of(), true);

        final DialogLayout layout = DialogLayout.of(menu);

        assertEquals(18, layout.exit().slot());
        assertEquals(List.of(13), slots(layout));
    }

    private static Menu list(boolean previousPage) {
        final Menu.Builder menu = Menu.builder("Bans in force (50)", 6).list()
                .set(0, button(MenuIcon.BAN, "#1"))
                .set(1, button(MenuIcon.BAN, "#2"))
                .set(2, button(MenuIcon.BAN, "#3"))
                .set(45, button(MenuIcon.BACK, "Back"))
                .set(46, button(MenuIcon.SORT, "Sort: newest"))
                .set(47, button(MenuIcon.FILTER, "Show: all"))
                .set(49, MenuItem.of(MenuIcon.PAGE, "Page 2/3", List.of("50 in total.", "Click to refresh.")).onClick(CLICK))
                .set(50, button(MenuIcon.NEXT, "Next page"))
                .set(52, button(MenuIcon.SEARCH, "Search and filter"))
                .set(53, button(MenuIcon.CLOSE, "Close"));
        if (previousPage) menu.set(48, button(MenuIcon.PREVIOUS, "Previous page"));
        return menu.build().styled("punishments", List.of(), true);
    }

    @Test
    void list_pageButtonsInFullRowsAboveTheEntries_pageAndOptionsAsText() {
        final DialogLayout layout = DialogLayout.of(list(false));

        assertEquals(List.of(50, 52, 0, 1, 2), slots(layout));
        assertEquals(45, layout.exit().slot());
        assertEquals(List.of(new DialogLayout.Line("Page 2/3§8  ·  50 in total."),
                new DialogLayout.Line("Sort: newest§8  ·  Show: all")), layout.body());
    }

    @Test
    void list_oddPageButtons_takeThePageButtonToFillTheRow() {
        final DialogLayout layout = DialogLayout.of(list(true));

        assertEquals(List.of(48, 50, 52, 49, 0, 1, 2), slots(layout));
        assertEquals(List.of(new DialogLayout.Line("Sort: newest§8  ·  Show: all")), layout.body());
    }
}
