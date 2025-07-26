package com.openmc.plugin.judicator.punish.handlers;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishService;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.openmc.plugin.judicator.punish.data.cache.PunishCache;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.stream.Stream;

public class MuteHandler implements PunishHandler{

    private final Judicator judicator;
    private final PunishmentBuilder punishmentBuilder;
    private final ConfigurationNode messagesNode;
    private final boolean announce;
    private final PunishService punishService;
    private final PunishCache cache;

    public MuteHandler(Judicator judicator, PunishmentBuilder punishmentBuilder) {
        this.judicator = judicator;
        this.punishmentBuilder = punishmentBuilder;
        this.messagesNode = judicator.getMessagesConfig();
        this.announce = judicator.getConfig().node("announce").getBoolean(true);
        this.punishService = judicator.getPunishService();
        this.cache = judicator.getPunishCache();
    }

    public Punishment handle() {
        final Punishment punishment = punishService.save(punishmentBuilder.build());
        this.mute(punishment);
        this.announce(punishment);
        return punishment;
    }

    private void mute(Punishment punishment) {
        final ProxyServer server = judicator.getServer();
        server.getScheduler().buildTask(judicator,
                () -> {
                    final TextComponent muteAlert = PunishUtils.getMessageList(messagesNode, punishment, "runners", "mute-alert");

                    final Stream<Player> affectedPlayers = punishment.getIpAddress()
                            .map(ip -> server.getAllPlayers().stream()
                                    .filter(player -> player.getRemoteAddress().getAddress().getHostAddress().equals(ip)))
                            .orElseGet(() -> server.getPlayer(punishment.getNickname()).stream());

                    affectedPlayers.forEach(player -> {
                        player.sendMessage(muteAlert);
                        cache.putMutePunishment(player.getUsername().toLowerCase(), punishment);
                    });
                }
        ).schedule();
    }

    private void announce(Punishment punishment) {
        if (announce) {
            final ProxyServer server = judicator.getServer();
            final TextComponent announcement = PunishUtils.getMessageList(messagesNode, punishment, "announcements", "mute");
            server.sendMessage(announcement);
        }
    }
}
