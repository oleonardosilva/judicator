package com.elless.judicator.api.warn;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A warn as it was stored when it was read (an immutable snapshot).
 *
 * @param id             the warn id shown by {@code /wview}
 * @param targetName     the name of the warned account, as it was when warned
 * @param targetUniqueId the UUID of the warned account; empty when it was not known
 * @param punisher       who applied it: a staff member, {@code Console}, or the name a plugin gave
 * @param reason         the reason
 * @param startedAt      when it was applied
 * @param endsAt         when a temporary warn ends; empty for a permanent one
 * @param status         where it stood when it was read
 */
public record WarnView(long id,
                       String targetName,
                       Optional<UUID> targetUniqueId,
                       String punisher,
                       String reason,
                       Instant startedAt,
                       Optional<Instant> endsAt,
                       WarnStatus status) {

    public WarnView {
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetUniqueId, "targetUniqueId");
        Objects.requireNonNull(punisher, "punisher");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(status, "status");
    }

    /**
     * @return whether it has no end
     */
    public boolean isPermanent() {
        return endsAt.isEmpty();
    }

    /**
     * @return whether it counted towards the warn tree when it was read
     */
    public boolean isActive() {
        return status == WarnStatus.ACTIVE;
    }

}
