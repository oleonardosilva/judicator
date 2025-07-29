package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.PunishmentBuilder;

public class PunishFactory {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;


    public PunishFactory(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public void factory() {
        try {
            if (punishmentBuilder.isBan()) new BanHandler(judicator, punishmentBuilder).handle();
            else new MuteHandler(judicator, punishmentBuilder).handle();

            judicator.getServer().getPlayer(punishmentBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "success", "punish-applied"))
            );
        } catch (Exception e) {
            judicator.getLogger().error("Failed to handle ban punishment", e);
            judicator.getServer().getPlayer(punishmentBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "error", "unknown"))
            );
        }
    }
}
