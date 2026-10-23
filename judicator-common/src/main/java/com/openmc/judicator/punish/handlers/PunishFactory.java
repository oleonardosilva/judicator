package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.api.event.PunishmentAppliedEvent;
import com.openmc.judicator.integration.ApiViews;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.rules.PunishCooldown;

import java.util.Optional;

public class PunishFactory {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;


    public PunishFactory(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public Optional<Punishment> factory() {
        // Checked again here: another staff member may have punished the same player while this one was confirming.
        final String category = PunishCooldown.categoryOf(punishmentBuilder.getType());
        final Optional<JudicatorPlayer> staff = punishmentBuilder.getStaff();
        if (staff.isPresent()) {
            if (!judicator.getPunishCooldown().acquire(staff.get(), category, punishmentBuilder.getNickname())) {
                return Optional.empty();
            }
        } else {
            judicator.getPunishCooldown().record(category, punishmentBuilder.getNickname());
        }
        try {
            final Punishment punishment;
            if (punishmentBuilder.isBan()) punishment = new BanHandler(judicator, punishmentBuilder).handle();
            else punishment = new MuteHandler(judicator, punishmentBuilder).handle();
            judicator.getEventBus().post(() -> new PunishmentAppliedEvent(ApiViews.punishment(punishment), punishment.isSilent()));

            judicator.getPlatform().getPlayer(punishmentBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(PunishUtils.getMessage(judicator.messagesFor(player), "success", "punish-applied"))
            );

            return Optional.of(punishment);
        } catch (Exception e) {
            judicator.getPunishCooldown().release(category, punishmentBuilder.getNickname());
            judicator.getLogger().error("Failed to handle ban punishment", e);
            judicator.getPlatform().getPlayer(punishmentBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(PunishUtils.getMessage(judicator.messagesFor(player), "error", "unknown"))
            );
            return Optional.empty();
        }
    }
}
