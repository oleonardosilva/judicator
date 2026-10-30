package com.elless.judicator.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.elless.judicator.Judicator;
import com.elless.judicator.commons.libs.DriverClassLoader;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.punish.listeners.ChatHandler;
import com.elless.judicator.punish.listeners.ConnectionHandler;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Optional;
import java.util.UUID;

/**
 * Judicator on a mod loader: the loader module calls these from its events, the same events on Fabric, NeoForge and
 * Forge. The core starts with the server ({@link #start}) and stops with it.
 */
public final class JudicatorMinecraft {

    private final MinecraftPlatform platform;
    private volatile Judicator judicator;
    private URLClassLoader drivers;

    public JudicatorMinecraft(MinecraftPlatform platform) {
        this.platform = platform;
    }

    public MinecraftPlatform platform() {
        return platform;
    }

    /**
     * The server is starting: the core loads its files and database, and its commands join those of the server.
     *
     * @return false when the core could not start (the reason is logged); the server goes on without Judicator
     */
    public boolean start(MinecraftServer server) {
        platform.server(server);
        final Judicator core = new Judicator(platform);
        try {
            drivers = DriverClassLoader.load(platform.getDataDirectory(), platform.getLogger());
            platform.databaseDrivers(drivers);
            core.enable();
        } catch (RuntimeException e) {
            platform.getLogger().error("Judicator could not be enabled; check the configuration files.", e);
            core.disable();
            return false;
        }
        judicator = core;
        registerCommands(server.getCommands().getDispatcher());
        platform.getLogger().info("Judicator has been initialized successfully!");
        return true;
    }

    public void stop() {
        final Judicator core = judicator;
        judicator = null;
        if (core != null) core.disable();
        platform.shutdown();
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
     * The commands of the core; called again when a reload of the data packs builds the server's commands anew.
     */
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        final Judicator core = judicator;
        if (core != null) new MinecraftCommands(core, platform).register(dispatcher);
    }

    /**
     * After authentication, before the player joins (off the game thread): bans on the address and the account, the
     * account limits of the address and banned alts.
     *
     * @return the screen to disconnect with; empty to let the player in
     */
    public Optional<net.minecraft.network.chat.Component> login(UUID uuid, String username, String address) {
        final Judicator core = judicator;
        if (core == null) return Optional.empty();
        final ConnectionHandler connections = core.getConnectionHandler();
        Optional<Component> kick = connections.checkPreLogin(username, address);
        if (kick.isEmpty()) kick = connections.checkLogin(uuid, username, address);
        if (kick.isPresent()) return kick.map(MinecraftText::toNative);
        connections.onLoginAllowed(uuid, username, address);
        return Optional.empty();
    }

    public void join(ServerPlayer player) {
        final MinecraftPlayer tracked = platform.track(player);
        final Judicator core = judicator;
        if (core != null) platform.runAsync(() -> core.getConnectionHandler().onJoin(tracked));
    }

    public void quit(ServerPlayer player) {
        final Optional<MinecraftPlayer> left = platform.untrack(player);
        final Judicator core = judicator;
        if (core == null || left.isEmpty()) return;
        final MinecraftPlayer gone = left.get();
        platform.runAsync(() -> core.getConnectionHandler().onDisconnect(gone.getUniqueId(), gone.getName(), gone.getHostAddress()));
    }

    /**
     * A chat message (game thread): taken by a prompt of a staff member, blocked for a muted player; a soft-muted
     * player only sees their own message echoed.
     *
     * @return whether the message goes to the chat
     */
    public boolean allowChat(ServerPlayer sender, String message) {
        final Judicator core = judicator;
        final Optional<JudicatorPlayer> player = platform.getPlayer(sender.getUUID());
        if (core == null || player.isEmpty()) return true;
        final ChatHandler chat = core.getChatHandler();
        if (chat.handlePromptChat(player.get(), message)) return false;
        return switch (chat.chatDecision(player.get())) {
            case BLOCK -> false;
            case SOFT -> {
                player.get().sendMessage(chat.softMuteEcho(player.get(), message));
                yield false;
            }
            default -> true;
        };
    }

    /**
     * A command a player runs (game thread, before it runs): taken by a prompt of a staff member, blocked for a muted
     * player when it is one of {@code blocked-commands}.
     *
     * @param commandLine without the slash
     * @return whether the command runs
     */
    public boolean allowCommand(ServerPlayer sender, String commandLine) {
        final Judicator core = judicator;
        final Optional<JudicatorPlayer> player = platform.getPlayer(sender.getUUID());
        if (core == null || player.isEmpty()) return true;
        final ChatHandler chat = core.getChatHandler();
        if (chat.handlePromptCommand(player.get(), commandLine)) return false;
        return !chat.shouldBlockCommand(player.get(), commandLine);
    }
}
