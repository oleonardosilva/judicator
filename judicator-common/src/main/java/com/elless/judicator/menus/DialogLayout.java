package com.elless.judicator.menus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * How a menu lays out on a screen of buttons (a dialog of 1.21.6+ clients), where the slots of an inventory mean
 * nothing: a body (texts, then the items without a click), a grid of buttons and one button below it, apart.
 * <ul>
 *     <li>The way out never mixes with the actions: it is the bottom button, which Escape presses too. Back where it
 *     returns to a screen, close on the first screen (where back would close anyway).</li>
 *     <li>A confirmation (no back nor close, a cancel) has cancel as that button; with a single other action, the two
 *     side by side ({@link #confirm()}).</li>
 *     <li>A list shows its page and its order and filters as text (the search button opens the form with them all),
 *     and the page buttons above the entries, in full rows: the page button (refresh, or clear the filters) joins them
 *     when they are odd, in place of the text with the page.</li>
 * </ul>
 */
public final class DialogLayout {

    /** columns of the grid; a last row with fewer buttons is centred */
    public static final int COLUMNS = 2;

    /**
     * Something of the body, in order.
     */
    public sealed interface Part permits Line, Content {
    }

    /**
     * A text with § colour codes.
     */
    public record Line(String text) implements Part {
    }

    /**
     * An item and its texts (an item without a click: what a screen shows, not what it does).
     */
    public record Content(MenuItem item) implements Part {
    }

    /**
     * A button: clicking it clicks that slot of the menu.
     */
    public record Button(int slot, MenuItem item) {
    }

    private final List<Part> body;
    private final List<Button> buttons;
    private final Button exit;
    private final Button confirm;

    private DialogLayout(List<Part> body, List<Button> buttons, Button exit, Button confirm) {
        this.body = List.copyOf(body);
        this.buttons = List.copyOf(buttons);
        this.exit = exit;
        this.confirm = confirm;
    }

    public List<Part> body() {
        return body;
    }

    /**
     * The grid, row by row; empty for a confirmation with yes and no only.
     */
    public List<Button> buttons() {
        return buttons;
    }

    /**
     * The bottom button (back, close or cancel), or null when the menu has none.
     */
    public Button exit() {
        return exit;
    }

    /**
     * The yes of a confirmation, beside {@link #exit()} (its no); null otherwise.
     */
    public Button confirm() {
        return confirm;
    }

    public static DialogLayout of(Menu menu) {
        final List<Part> lines = new ArrayList<>();
        menu.header().forEach(line -> lines.add(new Line(line)));
        final List<Part> contents = new ArrayList<>();
        final List<Button> actions = new ArrayList<>();
        final List<Button> controls = new ArrayList<>();
        final List<String> options = new ArrayList<>();
        Button back = null;
        Button close = null;
        Button page = null;
        for (Map.Entry<Integer, MenuItem> entry : menu.items().entrySet()) {
            final MenuItem item = entry.getValue();
            if (item.icon() == MenuIcon.FILLER) continue;
            if (item.action() == null) {
                contents.add(new Content(item));
                continue;
            }
            final Button button = new Button(entry.getKey(), item);
            switch (item.icon()) {
                case BACK -> back = back == null ? button : back;
                case CLOSE -> close = close == null ? button : close;
                case PAGE -> page = button;
                case SORT, FILTER, STATUS -> {
                    if (menu.isList()) options.add(item.name());
                    else actions.add(button);
                }
                default -> (menu.isList() && entry.getKey() >= MenuContext.NAVIGATION ? controls : actions).add(button);
            }
        }

        if (menu.isList()) {
            controls.sort(Comparator.comparingInt(DialogLayout::controlOrder));
            if (page != null && controls.size() % COLUMNS != 0) controls.add(page);
            else if (page != null) lines.add(new Line(pageText(page.item())));
            if (!options.isEmpty()) lines.add(new Line(String.join("§8  ·  ", options)));
        }

        Button exit = menu.hasPrevious() ? first(back, close) : first(close, back);
        Button confirm = null;
        if (exit == null) {
            final Button cancel = actions.stream().filter(button -> button.item().icon() == MenuIcon.CANCEL).findFirst().orElse(null);
            if (cancel != null) {
                actions.remove(cancel);
                exit = cancel;
                if (actions.size() == 1) confirm = actions.remove(0);
            }
        }

        final List<Part> body = new ArrayList<>(lines);
        body.addAll(contents);
        final List<Button> buttons = new ArrayList<>(controls);
        buttons.addAll(actions);
        return new DialogLayout(body, buttons, exit, confirm);
    }

    /**
     * Previous and next page first, then the search, then the rest (a list's own button, e.g. add a note).
     */
    private static int controlOrder(Button button) {
        return switch (button.item().icon()) {
            case PREVIOUS -> 0;
            case NEXT -> 1;
            case SEARCH -> 2;
            default -> 3 + button.slot();
        };
    }

    /**
     * "Page 2/3 · 120 in total": the name of the page button and the first line of its lore.
     */
    private static String pageText(MenuItem page) {
        return page.lore().isEmpty() ? page.name() : page.name() + "§8  ·  " + page.lore().get(0);
    }

    private static Button first(Button preferred, Button other) {
        return preferred != null ? preferred : other;
    }
}
