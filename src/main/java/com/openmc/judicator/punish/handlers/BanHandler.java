package com.openmc.judicator.punish.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

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
        final ProxyServer server = judicator.getServer();
        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        server.getScheduler().buildTask(judicator,
                () -> {
                    final TextComponent kickMessage = PunishUtils.getMessageList(messagesNode, punishment, "runners", "ban-kick");

                    punishment.getIpAddress()
                            .map(ip -> server.getAllPlayers().stream()
                                    .filter(player -> player.getRemoteAddress().getAddress().getHostAddress().equals(ip)))
                            .orElseGet(() -> server.getPlayer(punishment.getNickname()).stream())
                            .forEach(player -> player.disconnect(kickMessage));
                }
        ).schedule();
    }

    private void announce(Punishment punishment) {
        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        if (judicator.getConfig().node("announce").getBoolean(true)) {
            final ProxyServer server = judicator.getServer();
            server.getPlayer(punishment.getNickname()).flatMap(Player::getCurrentServer).ifPresent(serverConnection -> {
                final TextComponent announcement = PunishUtils.getMessageList(messagesNode, punishment, "announcements", "ban");
                serverConnection.getServer().sendMessage(announcement);
            });
        }
    }

}
