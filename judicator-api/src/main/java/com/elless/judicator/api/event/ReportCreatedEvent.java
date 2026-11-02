package com.elless.judicator.api.event;

import com.elless.judicator.api.report.ReportView;

import java.util.Objects;

/**
 * A player (or a plugin, through the API) reported someone.
 *
 * @param report the report, {@link com.elless.judicator.api.report.ReportStatus#OPEN}
 */
public record ReportCreatedEvent(ReportView report) implements JudicatorEvent {

    public ReportCreatedEvent {
        Objects.requireNonNull(report, "report");
    }

}
