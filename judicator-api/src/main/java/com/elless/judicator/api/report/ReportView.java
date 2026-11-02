package com.elless.judicator.api.report;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A player report ({@code /report}) as it was stored when it was read.
 *
 * @param id               the id {@code /reports info} takes
 * @param reporterName     who reported: a player, or the name a plugin gave
 * @param reporterUniqueId the reporter's UUID (a plugin's is made from its name)
 * @param targetName       the reported player's name
 * @param targetUniqueId   the reported account's UUID; empty for a name nobody had seen (offline mode)
 * @param reason           what the reporter wrote
 * @param server           the server the reported player was on; empty when not known
 * @param status           where it stands
 * @param assignee         the staff member handling it (or who closed it); empty while open
 * @param createdAt        when it was made
 * @param updatedAt        when it last changed
 * @param closedAt         when it was closed
 * @param closeComment     what the staff wrote when closing it
 */
public record ReportView(long id, String reporterName, UUID reporterUniqueId, String targetName, Optional<UUID> targetUniqueId,
                         String reason, Optional<String> server, ReportStatus status, Optional<String> assignee, Instant createdAt,
                         Instant updatedAt, Optional<Instant> closedAt, Optional<String> closeComment) {

    public ReportView {
        Objects.requireNonNull(reporterName, "reporterName");
        Objects.requireNonNull(reporterUniqueId, "reporterUniqueId");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetUniqueId, "targetUniqueId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(assignee, "assignee");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(closedAt, "closedAt");
        Objects.requireNonNull(closeComment, "closeComment");
    }
}
