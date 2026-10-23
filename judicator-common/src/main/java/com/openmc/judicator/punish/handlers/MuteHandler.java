package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;

import java.time.Duration;

public class MuteHandler implements PunishHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;

    public MuteHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public Punishment handle() {
        final Punishment built = punishmentBuilder.build();
        // An online-only mute of a player who is not online starts paused: its time runs from their next login.
        if (built.isOnlineOnly() && built.getFinishAt().isPresent()
                && judicator.getPlatform().getPlayer(built.getNickname()).isEmpty()) {
            built.setPausedRemaining(Duration.between(built.getStartedAt(), built.getFinishAt().get()).toMillis());
            built.setFinishAt(null);
        }
        final Punishment punishment = judicator.getPunishService().save(built);
        judicator.getDiscordWebhookService().sendPunishment(punishment);
        judicator.getPunishmentEffects().punished(punishment);
        return punishment;
    }
}
