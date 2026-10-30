package com.elless.judicator.menus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A menu as the platform draws it: a title and the items by slot, row by row from the top left.
 */
public final class Menu {

    public static final int COLUMNS = 9;

    private final String title;
    private final int rows;
    private final Map<Integer, MenuItem> items;
    private final String key;
    private final List<String> header;
    private final boolean list;
    private final boolean previous;

    private Menu(String title, int rows, Map<Integer, MenuItem> items, String key, List<String> header, boolean list,
                 boolean previous) {
        this.title = title;
        this.rows = rows;
        this.items = Collections.unmodifiableMap(new TreeMap<>(items));
        this.key = key;
        this.header = List.copyOf(header);
        this.list = list;
        this.previous = previous;
    }

    public static Builder builder(String title, int rows) {
        return new Builder(title, rows);
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * COLUMNS;
    }

    public Map<Integer, MenuItem> items() {
        return items;
    }

    public Optional<MenuItem> item(int slot) {
        return Optional.ofNullable(items.get(slot));
    }

    /**
     * Which screen this is ({@code main}, {@code player}, {@code punishments}...): what a renderer or a server's
     * resource pack can style per screen.
     */
    public String key() {
        return key;
    }

    /**
     * {@code menu.style.<key>.header} of the messages, empty by default: lines a renderer shows above the menu (a
     * dialog does; an inventory has no room), e.g. a banner drawn by the server's resource pack.
     */
    public List<String> header() {
        return header;
    }

    /**
     * A list of entries with pages, order and filters ({@link MenuContext#page}): what shows best as a grid of items
     * (an inventory) even where the rest of the menus are dialogs.
     */
    public boolean isList() {
        return list;
    }

    /**
     * Whether "back" returns to another screen; on the first screen of the stack it closes the menu (what a dialog
     * then shows as its way out).
     */
    public boolean hasPrevious() {
        return previous;
    }

    Menu styled(String key, List<String> header, boolean previous) {
        return new Menu(title, rows, items, key, header, list, previous);
    }

    public static final class Builder {

        private final String title;
        private final int rows;
        private final Map<Integer, MenuItem> items = new TreeMap<>();
        private boolean list;

        private Builder(String title, int rows) {
            this.title = title;
            this.rows = Math.max(1, Math.min(6, rows));
        }

        public Builder set(int slot, MenuItem item) {
            if (slot >= 0 && slot < rows * COLUMNS && item != null) items.put(slot, item);
            return this;
        }

        public Builder list() {
            this.list = true;
            return this;
        }

        public Menu build() {
            return new Menu(title, rows, items, "", List.of(), list, false);
        }
    }
}
