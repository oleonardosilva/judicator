package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Optional;

public class OnBuildingPunishListener {

    private final Judicator judicator;
    private final PunishCache punishCache;

    public OnBuildingPunishListener(Judicator judicator) {
        this.judicator = judicator;
        this.punishCache = judicator.getPunishCache();
    }

    public void register() {
        judicator.getServer().getEventManager().register(judicator, this);
    }

    @Subscribe
    private void onPlayerChat(PlayerChatEvent event) {
        final Player player = event.getPlayer();
        final Optional<ChatContext<PunishmentBuilder>> optContext = punishCache.getContext(player.getUsername());
        if (optContext.isEmpty()) return;
        final ChatContext<PunishmentBuilder> context = optContext.get();
        context.accept(event);
        event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe
    private void onPlayerCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        final Optional<ChatContext<PunishmentBuilder>> optContext = punishCache.getContext(player.getUsername());
        if (optContext.isEmpty()) return;
        final ChatContext<PunishmentBuilder> context = optContext.get();
        context.accept(event);
        event.setResult(CommandExecuteEvent.CommandResult.denied());
    }
}
