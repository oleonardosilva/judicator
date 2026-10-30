package com.elless.judicator.api.event;

import com.elless.judicator.api.punishment.PunishmentView;

import java.util.Objects;

/**
 * A ban or mute (including soft and IP ones) was applied and stored. Kicks are a {@link PlayerKickedEvent}.
 *
 * @param punishment the punishment as stored
 * @param silent     whether it was applied silently (not announced to the players)
 */
public record PunishmentAppliedEvent(PunishmentView punishment, boolean silent) implements JudicatorEvent {

    public PunishmentAppliedEvent {
        Objects.requireNonNull(punishment, "punishment");
    }

}
