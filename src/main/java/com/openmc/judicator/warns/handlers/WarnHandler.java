package com.openmc.judicator.warns.handlers;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.warns.*;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Optional;

public class WarnHandler {

    private final Judicator judicator;
    private final WarnBuilder warnBuilder;

    public WarnHandler(Judicator judicator, WarnBuilder warnBuilder) {
        this.judicator = judicator;
        this.warnBuilder = warnBuilder;
    }

    public Optional<Warn> handle() {
        try {
            final Warn warn = judicator.getWarnService().save(warnBuilder.build());
            this.announce(warn);
            this.calc(warn);

            judicator.getServer().getPlayer(warn.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(WarnUtils.getMessage(judicator.getMessagesConfig(), "success", "warn-applied"))
            );

            judicator.getServer().getPlayer(warn.getNickname()).ifPresent(player -> {
                final TextComponent warnAlert = WarnUtils.getMessageList(judicator.getMessagesConfig(), warn, "runners", "warn-alert");
                player.sendMessage(warnAlert);
            });

            return Optional.of(warn);
        } catch (Exception e) {
            judicator.getLogger().error("Failed to handle ban punishment", e);
            judicator.getServer().getPlayer(warnBuilder.getPunisher()).ifPresent(
                    player -> player
                            .sendMessage(WarnUtils.getMessage(judicator.getMessagesConfig(), "error", "unknown"))
            );
            return Optional.empty();
        }
    }

    private void calc(Warn warn) {
        final WarnService warnService = judicator.getWarnService();

        final Long count = warnService.countActiveWarns(warn.getNickname());
        judicator.getActionCache().getAction(count)
                .map(ConfiguredAction::getCommands)
                .ifPresent(actions -> actions.forEach(action -> {
                    final ConsoleCommandSource console = judicator.getServer().getConsoleCommandSource();
                    judicator.getServer().getCommandManager()
                            .executeAsync(console, action.replace("{nickname}", warn.getSafeNickname()));
                }));
    }

    private void announce(Warn warn) {
        final ConfigurationNode messagesNode = judicator.getMessagesConfig();
        if (judicator.getConfig().node("announce").getBoolean(true)) {
            final ProxyServer server = judicator.getServer();
            server.getPlayer(warn.getNickname()).flatMap(Player::getCurrentServer).ifPresent(serverConnection -> {
                final TextComponent announcement = WarnUtils.getMessageList(messagesNode, warn, "announcements", "warn");
                serverConnection.getServer().sendMessage(announcement);
            });
        }
    }

}
