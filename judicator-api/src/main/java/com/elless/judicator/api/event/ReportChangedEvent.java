package com.elless.judicator.api.event;

import com.elless.judicator.api.report.ReportView;

import java.util.Objects;

/**
 * A report was assigned, given back or closed ({@code /reports}, the menus or the API).
 *
 * @param report    the report as it is now ({@link ReportView#status()} says what happened)
 * @param changedBy who did it: a staff member, {@code Console}, or the name a plugin gave
 */
public record ReportChangedEvent(ReportView report, String changedBy) implements JudicatorEvent {

    public ReportChangedEvent {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(changedBy, "changedBy");
    }

}
