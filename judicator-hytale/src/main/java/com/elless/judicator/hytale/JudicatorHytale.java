package com.elless.judicator.hytale;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.elless.judicator.Judicator;
import com.elless.judicator.commons.command.CoreCommand;
import com.elless.judicator.commons.libs.DriverClassLoader;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.punish.listeners.ChatHandler;
import com.elless.judicator.punish.listeners.ConnectionHandler;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Optional;

/**
 * The Hytale plugin (experimental: compiled against the server API, not tried on a server yet). Hytale has no event
 * before a command runs: a muted player's commands are not blocked, and the evidence prompt is answered in the chat or
 * with {@code /jprompt}. Judicator's {@code /ban}, {@code /unban} and {@code /kick} take the place of Hytale's.
 */
public final class JudicatorHytale extends JavaPlugin {

    private HytalePlatform platform;
    private URLClassLoader drivers;
    private volatile Judicator judicator;

    public JudicatorHytale(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void start() {
        platform = new HytalePlatform(getDataDirectory(), new HytaleLog(getLogger()));
        final Judicator core = new Judicator(platform);
        try {
            drivers = DriverClassLoader.load(platform.getDataDirectory(), platform.getLogger());
            platform.databaseDrivers(drivers);
            core.enable();
        } catch (RuntimeException e) {
            platform.getLogger().error("Judicator could not be enabled; check the configuration files.", e);
            core.disable();
            return;
        }
        judicator = core;
        for (CoreCommand command : core.getCommands()) {
            register(core, command);
        }
        getEventRegistry().register(EventPriority.EARLY, PlayerSetupConnectEvent.class, this::onSetupConnect);
        getEventRegistry().register(PlayerConnectEvent.class, event -> onConnect(event.getPlayerRef()));
        getEventRegistry().register(PlayerDisconnectEvent.class, event -> onDisconnect(event.getPlayerRef()));
        getEventRegistry().registerAsyncGlobal(PlayerChatEvent.class, future -> future.thenApply(this::onChat));
        platform.getLogger().info("Judicator has been initialized successfully!");
    }

    @Override
    protected void shutdown() {
        final Judicator core = judicator;
        judicator = null;
        if (core != null) core.disable();
        if (platform != null) platform.shutdown();
        if (drivers != null) {
            try {
                drivers.close();
            } catch (IOException e) {
                platform.getLogger().warn("Could not close the database driver", e);
            }
            drivers = null;
        }
    }

    /**
     * In place of a Hytale command of the same name ({@code /ban}, {@code /unban}, {@code /kick}).
     */
    private void register(Judicator core, CoreCommand command) {
        try {
            if (CommandManager.get().getCommandRegistration().containsKey(command.getName())) {
                CommandManager.get().getCommandRegistration().remove(command.getName());
            }
        } catch (RuntimeException e) {
            platform.getLogger().warn("Could not take the place of Hytale's /{}", command.getName(), e);
        }
        try {
            getCommandRegistry().registerCommand(new HytaleCommand(core, platform, command));
        } catch (RuntimeException e) {
            platform.getLogger().warn("Could not register /{}", command.getName(), e);
        }
    }

    /**
     * Before the player joins: bans on the address and the account, the account limits of the address, banned alts.
     */
    private void onSetupConnect(PlayerSetupConnectEvent event) {
        final Judicator core = judicator;
        if (core == null || event.isCancelled()) return;
        final String address = HytalePlayer.hostOf(event.getPacketHandler().getChannel().remoteAddress());
        final ConnectionHandler connections = core.getConnectionHandler();
        try {
            Optional<Component> kick = connections.checkPreLogin(event.getUsername(), address);
            if (kick.isEmpty()) kick = connections.checkLogin(event.getUuid(), event.getUsername(), address);
            if (kick.isPresent()) {
                event.setCancelled(true);
                event.setReason(HytaleText.toMessage(kick.get()));
                return;
            }
            connections.onLoginAllowed(event.getUuid(), event.getUsername(), address);
        } catch (RuntimeException e) {
            platform.getLogger().error("Could not check the login of {}", event.getUsername(), e);
        }
    }

    private void onConnect(com.hypixel.hytale.server.core.universe.PlayerRef player) {
        final HytalePlayer tracked = platform.track(player);
        final Judicator core = judicator;
        if (core != null) platform.runAsync(() -> core.getConnectionHandler().onJoin(tracked));
    }

    private void onDisconnect(com.hypixel.hytale.server.core.universe.PlayerRef player) {
        final Optional<HytalePlayer> left = platform.untrack(player);
        final Judicator core = judicator;
        if (core == null || left.isEmpty()) return;
        final HytalePlayer gone = left.get();
        platform.runAsync(() -> core.getConnectionHandler().onDisconnect(gone.getUniqueId(), gone.getName(), gone.getHostAddress()));
    }

    /**
     * Taken by a staff member's prompt, blocked for a muted player; a soft-muted player only sees their own message.
     */
    private PlayerChatEvent onChat(PlayerChatEvent event) {
        final Judicator core = judicator;
        if (core == null || event.isCancelled()) return event;
        final Optional<JudicatorPlayer> player = platform.getPlayer(event.getSender().getUuid());
        if (player.isEmpty()) return event;
        final ChatHandler chat = core.getChatHandler();
        if (chat.handlePromptChat(player.get(), event.getContent())) {
            event.setCancelled(true);
            return event;
        }
        switch (chat.chatDecision(player.get())) {
            case BLOCK -> event.setCancelled(true);
            case SOFT -> {
                event.setCancelled(true);
                player.get().sendMessage(chat.softMuteEcho(player.get(), event.getContent()));
            }
            default -> {
            }
        }
        return event;
    }
}
