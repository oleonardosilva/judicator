package com.elless.judicator.api.report;

/**
 * Where a report stands.
 */
public enum ReportStatus {

    /** waiting for the staff */
    OPEN,
    /** a staff member took it ({@code /reports assign}) */
    ASSIGNED,
    /** done ({@code /reports close}) */
    CLOSED;

    /**
     * @return whether it still waits for the staff ({@link #OPEN} or {@link #ASSIGNED})
     */
    public boolean isOpen() {
        return this != CLOSED;
    }
}
