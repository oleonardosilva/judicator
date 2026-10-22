package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Optional;

public final class ChatHandler {

    private final Judicator judicator;
    private volatile List<String> blockedCommands = List.of();
    private volatile boolean blockGlobalChat;

    public ChatHandler(Judicator judicator) {
        this.judicator = judicator;
        reload();
    }

    public void reload() {
        try {
            final List<String> commands = judicator.getConfig().node("commands-locked-when-silenced").getList(String.class);
            this.blockedCommands = commands == null ? List.of() : List.copyOf(commands);
        } catch (SerializationException e) {
            judicator.getLogger().error(e.getMessage(), e);
            this.blockedCommands = List.of();
        }
        this.blockGlobalChat = judicator.getConfig().node("block-global-chat").getBoolean();
    }

    public boolean shouldBlockChat(JudicatorPlayer player) {
        if (!blockGlobalChat) return false;
        final Optional<Punishment> punishment = judicator.getPunishCache().getPunishment(player.getName());
        if (punishment.isEmpty()) return false;
        player.sendMessage(PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment.get(), "runners", "mute-status"));
        return true;
    }

    public boolean shouldBlockCommand(JudicatorPlayer player, String commandLine) {
        final Optional<Punishment> punishment = judicator.getPunishCache().getPunishment(player.getName());
        if (punishment.isEmpty()) return false;

        final String label = commandLine.split(" ")[0];
        if (blockedCommands.stream().noneMatch(s -> s.contains(label))) return false;

        player.sendMessage(PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment.get(), "runners", "mute-status"));
        return true;
    }

    public boolean handlePromptChat(JudicatorPlayer player, String message) {
        final PunishCache cache = judicator.getPunishCache();
        final Optional<ChatContext<PunishmentBuilder>> context = cache.getContext(player.getName());
        if (context.isEmpty()) return false;
        context.get().acceptChat(player, message);
        return true;
    }

    public boolean handlePromptCommand(JudicatorPlayer player, String commandLine) {
        final PunishCache cache = judicator.getPunishCache();
        final Optional<ChatContext<PunishmentBuilder>> context = cache.getContext(player.getName());
        if (context.isEmpty()) return false;
        context.get().acceptCommand(player, commandLine);
        return true;
    }

}
