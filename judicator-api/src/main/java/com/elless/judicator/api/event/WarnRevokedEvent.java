package com.elless.judicator.api.event;

import com.elless.judicator.api.warn.WarnView;

import java.util.Objects;

/**
 * A warn was taken back ({@code /unwarn}, {@code /staffrollback} or the API). Judicator deletes the warn, so it is no
 * longer in the history afterwards; this event carries it as it was. Temporary warns that end and old warns removed
 * by data retention post nothing.
 *
 * @param warn      the warn as it was before it was removed
 * @param revokedBy who removed it: a staff member, {@code Console}, or the name a plugin gave
 */
public record WarnRevokedEvent(WarnView warn, String revokedBy) implements JudicatorEvent {

    public WarnRevokedEvent {
        Objects.requireNonNull(warn, "warn");
        Objects.requireNonNull(revokedBy, "revokedBy");
    }

}
