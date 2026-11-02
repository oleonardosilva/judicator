package com.elless.judicator.api.event;

import com.elless.judicator.api.punishment.PunishmentView;

import java.util.Objects;

/**
 * A guard ({@code judicator.jail.guard}) caught a fugitive, who goes back to jail with the time they had left and
 * {@code jail.escape.caught-penalty}. Posted by the game server where it happened. Prisoners caught while trying to
 * leave (not yet fugitives) post nothing: their jail only gets longer.
 *
 * @param punishment the jail, in force again (no {@link PunishmentView#escapedAt()})
 * @param guard      the name of the guard
 */
public record PrisonerCaughtEvent(PunishmentView punishment, String guard) implements JudicatorEvent {

    public PrisonerCaughtEvent {
        Objects.requireNonNull(punishment, "punishment");
        Objects.requireNonNull(guard, "guard");
    }

}
