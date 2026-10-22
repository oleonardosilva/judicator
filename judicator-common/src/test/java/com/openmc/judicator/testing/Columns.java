package com.openmc.judicator.testing;

/**
 * Column rules of the relational tables, enforced by the in-memory repositories. The DAOs only log a rejected
 * statement, so in production a violation silently loses the record; in tests it fails loudly instead.
 */
final class Columns {

    private Columns() {
    }

    static void notNull(Object value, String column) {
        if (value == null) throw new IllegalStateException("The database would reject this row: " + column + " is NOT NULL");
    }

    static void maxLength(String value, int max, String column) {
        if (value != null && value.length() > max) {
            throw new IllegalStateException("The database would reject this row: " + column + " is VARCHAR(" + max
                    + ") but got " + value.length() + " characters");
        }
    }
}
