package com.elless.judicator.menus;

import com.elless.judicator.commons.ListQuery;

import java.util.List;

/**
 * How a list screen is shown now: its order, its filter, its status filter and a search. The values are keys of
 * {@code menu.lists.values}; the lists a screen offers are its {@link Spec}.
 */
public record ListOptions(String order, String filter, String status, String search) {

    public ListOptions {
        search = search == null ? "" : search.trim();
    }

    /**
     * What a list offers; the first value of each is the default. An empty list hides its button.
     */
    public record Spec(List<String> orders, List<String> filters, List<String> statuses) {

        public Spec {
            orders = List.copyOf(orders);
            filters = List.copyOf(filters);
            statuses = List.copyOf(statuses);
        }

        public ListOptions defaults() {
            return new ListOptions(first(orders), first(filters), first(statuses), "");
        }

        private static String first(List<String> values) {
            return values.isEmpty() ? "" : values.get(0);
        }
    }

    public ListOptions withOrder(String value) {
        return new ListOptions(value, filter, status, search);
    }

    public ListOptions withFilter(String value) {
        return new ListOptions(order, value, status, search);
    }

    public ListOptions withStatus(String value) {
        return new ListOptions(order, filter, value, search);
    }

    public ListOptions withSearch(String value) {
        return new ListOptions(order, filter, status, value);
    }

    public boolean searches() {
        return !search.isEmpty();
    }

    /**
     * The order and the search for a list read from the database.
     */
    public ListQuery query() {
        final ListQuery.Order sql = switch (order) {
            case "oldest" -> ListQuery.Order.OLDEST;
            case "ending" -> ListQuery.Order.ENDING;
            case "name" -> ListQuery.Order.NAME;
            default -> ListQuery.Order.NEWEST;
        };
        return new ListQuery(search, sql);
    }

    /**
     * Whether a value of the search is in one of the texts, as the database search does.
     */
    public boolean matches(String... values) {
        return query().matches(values);
    }

    static String next(List<String> values, String current) {
        if (values.isEmpty()) return current;
        final int index = values.indexOf(current);
        return values.get((index + 1) % values.size());
    }
}
