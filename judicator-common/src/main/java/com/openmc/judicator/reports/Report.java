package com.openmc.judicator.reports;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's report about another player ({@code /report}), handled by the staff with {@code /reports}.
 */
@Getter
@Setter
@NoArgsConstructor
public class Report {

    private Long id;
    private UUID reporterUuid;
    private String reporterName;
    /** null in offline mode for a name nobody had seen */
    private UUID targetUuid;
    private String targetName;
    private String reason;
    /** the server the reported player was on; null when not online or not known */
    private String server;
    private ReportState state = ReportState.OPEN;
    private UUID assigneeUuid;
    private String assigneeName;
    private LocalDateTime createdAt, updatedAt, closedAt;
    private String closeComment;

    public static Report create(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName, String reason, String server) {
        final Report report = new Report();
        final LocalDateTime now = LocalDateTime.now();
        report.reporterUuid = reporterUuid;
        report.reporterName = reporterName;
        report.targetUuid = targetUuid;
        report.targetName = targetName;
        report.reason = reason;
        report.server = server;
        report.createdAt = now;
        report.updatedAt = now;
        return report;
    }

    public Optional<UUID> getTargetUuid() {
        return Optional.ofNullable(targetUuid);
    }

    public Optional<String> getServer() {
        return Optional.ofNullable(server);
    }

    public Optional<String> getAssigneeName() {
        return Optional.ofNullable(assigneeName);
    }

    public Optional<UUID> getAssigneeUuid() {
        return Optional.ofNullable(assigneeUuid);
    }

    public Optional<LocalDateTime> getClosedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<String> getCloseComment() {
        return Optional.ofNullable(closeComment);
    }

}
