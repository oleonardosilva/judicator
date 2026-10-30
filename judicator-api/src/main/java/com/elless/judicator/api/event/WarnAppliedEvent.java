package com.elless.judicator.api.event;

import com.elless.judicator.api.warn.WarnView;

import java.util.Objects;

/**
 * A warn was applied and stored. It is posted before the warn tree runs its commands, so a punishment the tree
 * applies arrives after it as its own event.
 *
 * @param warn   the warn as stored
 * @param silent whether it was applied silently (not announced to the players)
 */
public record WarnAppliedEvent(WarnView warn, boolean silent) implements JudicatorEvent {

    public WarnAppliedEvent {
        Objects.requireNonNull(warn, "warn");
    }

}
