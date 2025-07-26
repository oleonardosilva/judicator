package com.openmc.plugin.judicator.punish.handlers;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;

public class PunishFactory {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;


    public PunishFactory(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public void factory() {
        if (punishmentBuilder.isBan()) {
            new BanHandler(judicator, punishmentBuilder).handle();
            return;
        }

        new MuteHandler(judicator, punishmentBuilder).handle();
    }
}
