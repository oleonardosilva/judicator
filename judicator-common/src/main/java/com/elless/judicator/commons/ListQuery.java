package com.elless.judicator.commons;

import java.util.Locale;

/**
 * How a long list read from the database (the punishments in force, the reports) is searched and ordered.
 *
 * @param search text the player's name, the reason or the author must contain, ignoring case; empty for all
 */
public record ListQuery(String search, Order order) {

    public static final ListQuery NEWEST = new ListQuery("", Order.NEWEST);

    public enum Order {
        /** started or created last first */
        NEWEST,
        OLDEST,
        /** the ones ending soonest first, permanent ones last (punishments; reports read it as NEWEST) */
        ENDING,
        /** by the player's name (the reported player for reports) */
        NAME
    }

    public ListQuery {
        search = search == null ? "" : search.trim();
        order = order == null ? Order.NEWEST : order;
    }

    public boolean searches() {
        return !search.isEmpty();
    }

    /**
     * The search as a LIKE pattern with {@code !} as the escape character, so {@code %} and {@code _} typed by a
     * player are taken literally: {@code LIKE ? ESCAPE '!'} (the same in SQLite, MySQL and PostgreSQL).
     */
    public String likePattern() {
        final String escaped = search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /**
     * What the in-memory repositories compare with {@code LIKE}: whether one of the values contains the search.
     */
    public boolean matches(String... values) {
        if (!searches()) return true;
        final String wanted = search.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(wanted)) return true;
        }
        return false;
    }
}
