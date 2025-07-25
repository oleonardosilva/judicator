package com.openmc.plugin.judicator.punish.handlers;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

public class BanHandler {

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;
    private final ConfigurationNode messagesNode;
    private final boolean announce;

    public BanHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
        this.messagesNode = judicator.getMessagesConfig();
        this.announce = judicator.getConfig().node("announce").getBoolean(true);
    }

    public void handle() {
        final Punishment punishment = judicator.getPunishService().save(punishmentBuilder.build());
        this.kick(punishment);
        this.announce(punishment);
    }

    private void kick(Punishment punishment) {
        final ProxyServer server = judicator.getServer();
        server.getScheduler().buildTask(judicator,
                () -> {
                    final TextComponent kickMessage = PunishUtils.getMessageList(messagesNode, punishment, "runners", "ban-kick");

                    punishment.getIpAddress()
                            .ifPresentOrElse(
                                    (s) ->
                                            server.getAllPlayers()
                                                    .stream()
                                                    .filter(player -> player.getRemoteAddress().getAddress().getHostAddress().equals(s))
                                                    .forEach(player -> player.disconnect(kickMessage)),
                                    () -> server.getPlayer(punishment.getNickname())
                                            .ifPresent(player -> player.disconnect(kickMessage)));

                }
        ).schedule();
    }

    private void announce(Punishment punishment) {
        if (announce) {
            final ProxyServer server = judicator.getServer();
            final TextComponent announcement = PunishUtils.getMessageList(messagesNode, punishment, "announcements", "ban");
            server.sendMessage(announcement);
        }
    }

}
