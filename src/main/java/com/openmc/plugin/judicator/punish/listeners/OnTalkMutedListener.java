package com.openmc.plugin.judicator.punish.listeners;

import com.openmc.plugin.judicator.Judicator;
import com.openmc.plugin.judicator.punish.PunishUtils;
import com.openmc.plugin.judicator.punish.Punishment;
import com.openmc.plugin.judicator.punish.data.cache.PunishCache;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.TextComponent;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Optional;

public class OnTalkMutedListener {

    private final Judicator judicator;
    private final PunishCache punishCache;
    private final ConfigurationNode messagesNode;
    private final List<String> blockedCommands;
    private final boolean blockGlobalChat;

    public OnTalkMutedListener(Judicator judicator) throws SerializationException {
        this.judicator = judicator;
        this.punishCache = judicator.getPunishCache();
        this.messagesNode = judicator.getMessagesConfig();
        this.blockedCommands = judicator.getConfig().node("commands-locked-when-silenced").getList(String.class);
        this.blockGlobalChat = judicator.getConfig().node("block-global-chat").getBoolean();
    }

    public void register() {
        judicator.getServer().getEventManager().register(judicator, this);
    }

    @Subscribe(priority = 10)
    private void onPlayerChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed() || !blockGlobalChat) return;
        final Player player = event.getPlayer();
        final Optional<Punishment> optContext = punishCache.getPunishment(player.getUsername());
        if (optContext.isEmpty()) return;
        final Punishment punishment = optContext.get();
        final TextComponent muteAlert = PunishUtils.getMessageList(messagesNode, punishment, "runners", "mute-status");
        player.sendMessage(muteAlert);
        event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe(priority = 10)
    private void onPlayerCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player) || !event.getResult().isAllowed()) {
            return;
        }

        final Optional<Punishment> optContext = punishCache.getPunishment(player.getUsername());

        if (optContext.isEmpty() || blockedCommands.stream().noneMatch(s -> s.contains(event.getCommand().split(" ")[0]))) {
            return;
        }

        final Punishment punishment = optContext.get();
        final TextComponent muteAlert = PunishUtils.getMessageList(messagesNode, punishment, "runners", "mute-status");
        event.getCommandSource().sendMessage(muteAlert);
        event.setResult(CommandExecuteEvent.CommandResult.denied());
    }

}
