package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.PunishmentBuilder;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.types.PunishStatus;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.Locale;
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
        final Optional<Punishment> punishment = activeMute(player);
        if (punishment.isEmpty()) return false;
        player.sendMessage(PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment.get(), "runners", "mute-status"));
        return true;
    }

    public boolean shouldBlockCommand(JudicatorPlayer player, String commandLine) {
        final String label = label(commandLine);
        if (label.isEmpty() || blockedCommands.stream().map(ChatHandler::label).noneMatch(label::equals)) return false;

        final Optional<Punishment> punishment = activeMute(player);
        if (punishment.isEmpty()) return false;

        player.sendMessage(PunishUtils.getMessageList(judicator.getMessagesConfig(), punishment.get(), "runners", "mute-status"));
        return true;
    }

    /**
     * The mute that applies to the player: the one cached under their name (applied while online, or restored at
     * login) or under their address (IP mute restored at login, which also covers new accounts on that address).
     * A cached mute that has ended or was revoked meanwhile is dropped instead of blocking until the next login.
     */
    private Optional<Punishment> activeMute(JudicatorPlayer player) {
        final PunishCache cache = judicator.getPunishCache();
        return stillActive(cache, player.getName()).or(() -> stillActive(cache, player.getHostAddress()));
    }

    private static Optional<Punishment> stillActive(PunishCache cache, String key) {
        final Optional<Punishment> punishment = cache.getPunishment(key);
        if (punishment.isPresent() && punishment.get().getStatus() != PunishStatus.ACTIVE) {
            cache.removeMutePunishment(key);
            return Optional.empty();
        }
        return punishment;
    }

    /**
     * "/Tell Bob hi", "tell Bob hi" (Velocity) and "/minecraft:tell" all give "tell".
     */
    private static String label(String commandLine) {
        String label = commandLine.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.startsWith("/")) label = label.substring(1);
        final int namespace = label.indexOf(':');
        return namespace >= 0 ? label.substring(namespace + 1) : label;
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
