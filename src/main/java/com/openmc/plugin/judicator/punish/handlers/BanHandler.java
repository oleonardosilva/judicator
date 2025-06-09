package com.openmc.plugin.judicator.punish.handlers;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

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
                    final TextComponent.Builder text = Component.text();
                    try {
                        final List<String> list = messagesNode.node("runners", "ban-kick").getList(String.class);
                        if (list != null) {
                            PunishUtils.applyPlaceHolders(messagesNode, list, punishment).forEach(
                                    s -> text.append(Component.text(s))
                            );
                        }
                    } catch (SerializationException e) {
                        throw new RuntimeException(e);
                    }
                    final TextComponent kickMessage = text.build();

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
            final TextComponent.Builder text = Component.text();
            try {
                final List<String> list = messagesNode.node("announcements", "ban").getList(String.class);
                if (list != null) {
                    PunishUtils.applyPlaceHolders(messagesNode, list, punishment).forEach(
                            s -> text.append(Component.text(s))
                    );
                }
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            final TextComponent announcement = text.build();
            server.sendMessage(announcement);
        }
    }

}
