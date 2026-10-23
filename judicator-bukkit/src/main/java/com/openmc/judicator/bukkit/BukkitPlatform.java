package com.openmc.judicator.bukkit;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.platform.PlatformType;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BukkitPlatform implements Platform {

    private final JavaPlugin plugin;
    private final BukkitAudiences audiences;
    private final Logger logger;
    private final Map<String, BukkitPlayer> onlinePlayers = new ConcurrentHashMap<>();

    public BukkitPlatform(JavaPlugin plugin, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.audiences = audiences;
        this.logger = new JulLoggerAdapter(plugin.getLogger());
    }

    void trackOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            track(player);
        }
    }

    void track(Player player) {
        onlinePlayers.put(player.getName().toLowerCase(), new BukkitPlayer(player, this));
    }

    void untrack(Player player) {
        onlinePlayers.computeIfPresent(player.getName().toLowerCase(),
                (name, tracked) -> tracked.isHandle(player) ? null : tracked);
    }

    void runNextTick(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    BukkitAudiences audiences() {
        return audiences;
    }

    @Override
    public PlatformType getType() {
        return PlatformType.BUKKIT;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Path getDataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public boolean isOnlineMode() {
        return Bukkit.getOnlineMode();
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(String username) {
        return Optional.ofNullable(onlinePlayers.get(username.toLowerCase()));
    }

    @Override
    public Collection<? extends JudicatorPlayer> getOnlinePlayers() {
        return List.copyOf(onlinePlayers.values());
    }

    @Override
    public List<String> matchPlayerNames(String prefix) {
        final String lower = prefix.toLowerCase();
        final BukkitPlayer exact = onlinePlayers.get(lower);
        if (exact != null) return List.of(exact.getName());
        return onlinePlayers.values().stream()
                .map(BukkitPlayer::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .toList();
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        final long ticks = Math.max(1L, unit.toMillis(period) / 50L);
        final BukkitTask scheduled = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, ticks, ticks);
        return scheduled::cancel;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        runSync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        getOnlinePlayers().forEach(recipient -> recipient.sendMessage(message.apply(recipient)));
    }

}
