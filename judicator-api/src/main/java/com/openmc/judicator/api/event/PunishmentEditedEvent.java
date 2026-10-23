package com.openmc.judicator.api.event;

import com.openmc.judicator.api.punishment.PunishmentView;

import java.util.Objects;

/**
 * A punishment in force was changed without being revoked: {@code /extend} (a later end) or {@code /changereason}.
 * Online-only mutes pausing and resuming as the player leaves and joins are not edits and post nothing.
 *
 * @param previous the punishment before the change
 * @param current  the punishment after the change
 * @param editedBy who changed it: a staff member or {@code Console}
 */
public record PunishmentEditedEvent(PunishmentView previous, PunishmentView current, String editedBy) implements JudicatorEvent {

    public PunishmentEditedEvent {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(editedBy, "editedBy");
    }

    /**
     * @return whether the end (or what is left of a paused online-only mute) changed
     */
    public boolean durationChanged() {
        return !previous.endsAt().equals(current.endsAt()) || !previous.pausedRemaining().equals(current.pausedRemaining());
    }

    /**
     * @return whether the reason changed
     */
    public boolean reasonChanged() {
        return !previous.reason().equals(current.reason());
    }

}
