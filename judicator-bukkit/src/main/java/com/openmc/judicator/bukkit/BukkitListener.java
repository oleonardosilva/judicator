package com.openmc.judicator.bukkit;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.punish.listeners.ChatHandler;
import com.openmc.judicator.punish.listeners.ConnectionHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;

public class BukkitListener implements Listener {

    private final BukkitPlatform platform;
    private final ConnectionHandler connectionHandler;
    private final ChatHandler chatHandler;

    BukkitListener(Judicator judicator, BukkitPlatform platform) {
        this.platform = platform;
        this.connectionHandler = judicator.getConnectionHandler();
        this.chatHandler = judicator.getChatHandler();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        final String username = event.getName();
        final String address = event.getAddress().getHostAddress();

        Optional<Component> kick = connectionHandler.checkPreLogin(username, address);
        if (kick.isEmpty()) {
            kick = connectionHandler.checkLogin(event.getUniqueId(), username, address);
        }

        if (kick.isPresent()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    LegacyComponentSerializer.legacySection().serialize(kick.get()));
            return;
        }
        connectionHandler.onLoginAllowed(username, address);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        platform.track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final String username = player.getName();
        final UUID uuid = player.getUniqueId();
        final String address = platform.getPlayer(username).map(JudicatorPlayer::getHostAddress).orElse("");
        platform.untrack(player);
        platform.runNextTick(() -> {
            if (Bukkit.getPlayer(uuid) != null) return;
            platform.runAsync(() -> connectionHandler.onDisconnect(username, address));
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMutedChat(AsyncPlayerChatEvent event) {
        platform.getPlayer(event.getPlayer().getName()).ifPresent(player -> {
            if (chatHandler.shouldBlockChat(player)) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMutedCommand(PlayerCommandPreprocessEvent event) {
        platform.getPlayer(event.getPlayer().getName()).ifPresent(player -> {
            if (chatHandler.shouldBlockCommand(player, stripSlash(event.getMessage()))) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPromptChat(AsyncPlayerChatEvent event) {
        platform.getPlayer(event.getPlayer().getName()).ifPresent(player -> {
            if (chatHandler.handlePromptChat(player, event.getMessage())) event.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPromptCommand(PlayerCommandPreprocessEvent event) {
        platform.getPlayer(event.getPlayer().getName()).ifPresent(player -> {
            if (chatHandler.handlePromptCommand(player, stripSlash(event.getMessage()))) event.setCancelled(true);
        });
    }

    private static String stripSlash(String message) {
        return message.startsWith("/") ? message.substring(1) : message;
    }

}
