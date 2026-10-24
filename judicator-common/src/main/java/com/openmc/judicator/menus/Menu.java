package com.openmc.judicator.menus;

import java.util.Collections;
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

    private Menu(String title, int rows, Map<Integer, MenuItem> items) {
        this.title = title;
        this.rows = rows;
        this.items = Collections.unmodifiableMap(new TreeMap<>(items));
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

    public static final class Builder {

        private final String title;
        private final int rows;
        private final Map<Integer, MenuItem> items = new TreeMap<>();

        private Builder(String title, int rows) {
            this.title = title;
            this.rows = Math.max(1, Math.min(6, rows));
        }

        public Builder set(int slot, MenuItem item) {
            if (slot >= 0 && slot < rows * COLUMNS && item != null) items.put(slot, item);
            return this;
        }

        public Menu build() {
            return new Menu(title, rows, items);
        }
    }
}
