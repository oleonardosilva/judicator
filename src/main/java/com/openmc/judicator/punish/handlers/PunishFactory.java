package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;

import java.util.Optional;

public class PunishFactory {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;


    public PunishFactory(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public Optional<Punishment> factory() {
        try {
            final Punishment punishment;
            if (punishmentBuilder.isBan()) punishment = new BanHandler(judicator, punishmentBuilder).handle();
            else punishment = new MuteHandler(judicator, punishmentBuilder).handle();

            judicator.getServer().getPlayer(punishmentBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "success", "punish-applied"))
            );

            return Optional.of(punishment);
        } catch (Exception e) {
            judicator.getLogger().error("Failed to handle ban punishment", e);
            judicator.getServer().getPlayer(punishmentBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(PunishUtils.getMessage(judicator.getMessagesConfig(), "error", "unknown"))
            );
            return Optional.empty();
        }
    }
}
