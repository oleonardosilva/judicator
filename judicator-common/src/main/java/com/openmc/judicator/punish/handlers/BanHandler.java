package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

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
        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        platform.runSync(() -> {
            final TextComponent kickMessage = PunishUtils.getMessageList(messagesNode, punishment, "runners", "ban-kick");
            affectedPlayers(platform, punishment).forEach(player -> player.disconnect(kickMessage));
        });
    }

    private void announce(Punishment punishment) {
        judicator.getDiscordWebhookService().sendPunishment(punishment);

        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        judicator.getStaffNotifier().notifyPunishment(punishment);
        if (!punishment.isSilent() && judicator.getConfig().node("announce").getBoolean(true)) {
            final Platform platform = judicator.getPlatform();
            platform.getPlayer(punishment.getNickname()).ifPresent(player -> {
                final TextComponent announcement = PunishUtils.getMessageList(messagesNode, punishment, "announcements", "ban");
                platform.announceToServerOf(player, announcement);
            });
        }
    }

    static Stream<? extends JudicatorPlayer> affectedPlayers(Platform platform, Punishment punishment) {
        return punishment.getIpAddress()
                .<Stream<? extends JudicatorPlayer>>map(ip -> platform.getOnlinePlayers().stream()
                        .filter(player -> player.getHostAddress().equals(ip)))
                .orElseGet(() -> platform.getPlayer(punishment.getNickname()).stream());
    }

}
