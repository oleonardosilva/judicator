package com.openmc.judicator.velocity;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.platform.PlatformType;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class VelocityPlatform implements Platform {

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    public VelocityPlatform(Object plugin, ProxyServer server, Logger logger, Path dataDirectory) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Override
    public PlatformType getType() {
        return PlatformType.VELOCITY;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Override
    public boolean isOnlineMode() {
        return server.getConfiguration().isOnlineMode();
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(String username) {
        return server.getPlayer(username).map(VelocityPlayer::new);
    }

    @Override
    public Collection<? extends JudicatorPlayer> getOnlinePlayers() {
        return server.getAllPlayers().stream().map(VelocityPlayer::new).toList();
    }

    @Override
    public List<String> matchPlayerNames(String prefix) {
        return server.matchPlayer(prefix.toLowerCase()).stream().map(Player::getUsername).toList();
    }

    @Override
    public void runAsync(Runnable task) {
        server.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public void runSync(Runnable task) {
        runAsync(task);
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        final ScheduledTask scheduled = server.getScheduler().buildTask(plugin, task)
                .repeat(period, unit)
                .schedule();
        return scheduled::cancel;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Component message) {
        server.getPlayer(player.getUniqueId())
                .flatMap(Player::getCurrentServer)
                .ifPresent(connection -> connection.getServer().sendMessage(message));
    }

}
