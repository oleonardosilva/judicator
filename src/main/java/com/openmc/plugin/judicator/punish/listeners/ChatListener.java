package com.openmc.plugin.judicator.punish.listeners;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.commons.ChatContext;
import com.openmc.plugin.judicator.punish.PunishCache;
import com.openmc.plugin.judicator.punish.PunishmentBuilder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Optional;

public class ChatListener {

    private final Judicator judicator;
    private final PunishCache punishCache;

    public ChatListener(Judicator judicator) {
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
}
