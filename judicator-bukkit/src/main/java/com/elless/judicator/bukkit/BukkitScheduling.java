package com.elless.judicator.bukkit;

import com.elless.judicator.platform.PlatformTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spigot and Paper: one game thread for everything, players included.
 */
final class BukkitScheduling implements Scheduling {

    private final Plugin plugin;

    BukkitScheduling(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public PlatformTask asyncRepeating(Runnable task, long period, TimeUnit unit) {
        final long ticks = Math.max(1L, unit.toMillis(period) / 50L);
        final BukkitTask scheduled = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, ticks, ticks);
        return scheduled::cancel;
    }

    @Override
    public void global(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void nextTick(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void player(UUID player, Runnable task) {
        global(task);
    }

    @Override
    public void playerNextTick(UUID player, Runnable task) {
        nextTick(task);
    }

    @Override
    public CompletableFuture<Boolean> teleport(UUID moving, UUID destination) {
        final CompletableFuture<Boolean> done = new CompletableFuture<>();
        global(() -> {
            final Player from = Bukkit.getPlayer(moving);
            final Player to = Bukkit.getPlayer(destination);
            done.complete(from != null && to != null && from.teleport(to));
        });
        return done;
    }

}
