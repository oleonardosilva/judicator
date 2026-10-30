package com.elless.judicator.reports;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a report stands: waiting for the staff, being handled by one staff member, or done.
 */
public enum ReportState {

    OPEN,
    ASSIGNED,
    CLOSED;

    public boolean isOpen() {
        return this != CLOSED;
    }

    public static Optional<ReportState> parse(String name) {
        for (ReportState state : values()) {
            if (state.name().equalsIgnoreCase(name)) return Optional.of(state);
        }
        return Optional.empty();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
