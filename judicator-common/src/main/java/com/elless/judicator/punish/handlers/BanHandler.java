package com.elless.judicator.punish.handlers;

import com.elless.judicator.Judicator;
import com.elless.judicator.punish.Punishment;
import com.elless.judicator.punish.PunishmentBuilder;

public class BanHandler implements PunishHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;

    public BanHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public Punishment handle() {
        final Punishment punishment = judicator.getPunishService().save(punishmentBuilder.build());
        judicator.getDiscordWebhookService().sendPunishment(punishment);
        judicator.getPunishmentEffects().punished(punishment);
        return punishment;
    }

}
