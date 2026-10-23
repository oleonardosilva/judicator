package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;

import java.util.stream.Stream;

public class BanHandler implements PunishHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;

    public BanHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
    }

    public Punishment handle() {
        final Punishment punishment = judicator.getPunishService().save(punishmentBuilder.build());
        this.announce(punishment);
        this.kick(punishment);
        return punishment;
    }

    private void kick(Punishment punishment) {
        final Platform platform = judicator.getPlatform();
        platform.runSync(() -> affectedPlayers(platform, punishment).forEach(player -> player.disconnect(
                PunishUtils.getMessageList(judicator.messagesFor(player), punishment, "runners", "ban-kick"))));
    }

    private void announce(Punishment punishment) {
        judicator.getDiscordWebhookService().sendPunishment(punishment);

        judicator.getStaffNotifier().notifyPunishment(punishment);
        if (!punishment.isSilent() && judicator.getConfig().node("announce").getBoolean(true)) {
            final Platform platform = judicator.getPlatform();
            platform.getPlayer(punishment.getNickname()).ifPresent(player -> platform.announceToServerOf(player,
                    recipient -> PunishUtils.getMessageList(judicator.messagesFor(recipient), punishment, "announcements", "ban")));
        }
    }

    static Stream<? extends JudicatorPlayer> affectedPlayers(Platform platform, Punishment punishment) {
        return punishment.getIpAddress()
                .<Stream<? extends JudicatorPlayer>>map(ip -> platform.getOnlinePlayers().stream()
                        .filter(player -> player.getHostAddress().equals(ip)))
                .orElseGet(() -> platform.getPlayer(punishment.getNickname()).stream());
    }

}
