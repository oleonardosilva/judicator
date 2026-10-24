package com.openmc.judicator.punish.listeners;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.commons.ChatContext;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.PunishService;
import com.openmc.judicator.punish.PunishUtils;
import com.openmc.judicator.punish.Punishment;
import com.openmc.judicator.punish.data.cache.PunishCache;
import com.openmc.judicator.punish.types.PunishStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ChatHandler {

    /**
     * What happens to a chat message: it goes through, it is blocked (the player is told why), or — soft mute — only
     * the sender sees it and nothing tells them so.
     */
    public enum ChatDecision {
        ALLOW,
        BLOCK,
        SOFT
    }

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

    public ChatDecision chatDecision(JudicatorPlayer player) {
        if (!blockGlobalChat) return ChatDecision.ALLOW;
        final Optional<Punishment> punishment = activeMute(player);
        if (punishment.isEmpty()) return ChatDecision.ALLOW;
        if (punishment.get().isSoft()) return ChatDecision.SOFT;
        player.sendMessage(PunishUtils.getMessageList(judicator.messagesFor(player), punishment.get(), "runners", "mute-status"));
        return ChatDecision.BLOCK;
    }

    /**
     * The message a soft-muted player sees instead of the real chat line, where the platform cannot keep it for the
     * sender only (Velocity): {@code soft-mute-format} with the player's text as plain text (no colour codes).
     */
    public Component softMuteEcho(JudicatorPlayer player, String message) {
        final String format = judicator.getConfig().node("soft-mute-format").getString("<{player}> {message}");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(format.replace("{player}", player.getName()))
                .replaceText(builder -> builder.matchLiteral("{message}").replacement(Component.text(message)));
    }

    public boolean shouldBlockCommand(JudicatorPlayer player, String commandLine) {
        final String label = label(commandLine);
        if (label.isEmpty() || blockedCommands.stream().map(ChatHandler::label).noneMatch(label::equals)) return false;

        final Optional<Punishment> punishment = activeMute(player);
        if (punishment.isEmpty()) return false;
        if (punishment.get().isSoft()) return true;

        player.sendMessage(PunishUtils.getMessageList(judicator.messagesFor(player), punishment.get(), "runners", "mute-status"));
        return true;
    }

    /**
     * The mute that applies to the player where they are: among those cached under their UUID (applied while online,
     * or restored at login) and under their address (IP mutes restored at login, which also cover new accounts on that
     * address), the heaviest one whose scope covers their server. A cached mute that has ended or was revoked meanwhile
     * is dropped instead of blocking until the next login.
     */
    private Optional<Punishment> activeMute(JudicatorPlayer player) {
        final PunishCache cache = judicator.getPunishCache();
        final List<Punishment> mutes = new ArrayList<>(stillActive(cache, PunishService.cacheKey(player)));
        mutes.addAll(stillActive(cache, player.getHostAddress()));
        return mutes.stream()
                .filter(mute -> judicator.getScopeService().appliesToPlayer(mute, player))
                .min(PunishService.heavyFirst());
    }

    private static List<Punishment> stillActive(PunishCache cache, String key) {
        final List<Punishment> active = new ArrayList<>();
        for (Punishment mute : cache.getMutes(key)) {
            if (mute.getStatus() == PunishStatus.ACTIVE) active.add(mute);
            else cache.removeMutePunishment(key, mute.getId());
        }
        return active;
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
        final Optional<ChatContext<?>> context = cache.getContext(player.getName());
        if (context.isEmpty()) return false;
        context.get().acceptChat(player, message);
        return true;
    }

    public boolean handlePromptCommand(JudicatorPlayer player, String commandLine) {
        final PunishCache cache = judicator.getPunishCache();
        final Optional<ChatContext<?>> context = cache.getContext(player.getName());
        if (context.isEmpty()) return false;
        context.get().acceptCommand(player, commandLine);
        return true;
    }

}
