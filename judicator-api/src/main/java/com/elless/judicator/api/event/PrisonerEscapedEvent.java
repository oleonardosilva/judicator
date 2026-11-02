package com.elless.judicator.api.event;

import com.elless.judicator.api.punishment.PunishmentView;

import java.util.Objects;

/**
 * A prisoner escaped through a secret escape route of their jail. Posted by the game server where it happened.
 *
 * @param punishment the jail, now with {@link PunishmentView#escapedAt()}; it ends ({@link PunishmentView#endsAt()})
 *                   when the guards stop looking for the fugitive, and at once without {@code jail.escape.fugitive-time}
 */
public record PrisonerEscapedEvent(PunishmentView punishment) implements JudicatorEvent {

    public PrisonerEscapedEvent {
        Objects.requireNonNull(punishment, "punishment");
    }

}
