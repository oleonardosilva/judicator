package com.elless.judicator.bukkit;

import com.elless.judicator.platform.PlatformTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Folia: each region of the world ticks on a thread of its own and a player belongs to the region they are in, so what
 * touches a player runs on their entity scheduler and what is about the whole server (the console) on the global
 * region. Compiled against the Paper API of 1.20.1, the first Folia in use; created by {@link Scheduling#of} only on Folia.
 */
final class FoliaScheduling implements Scheduling {

    private final Plugin plugin;

    FoliaScheduling(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    @Override
    public PlatformTask asyncRepeating(Runnable task, long period, TimeUnit unit) {
        final long millis = Math.max(1L, unit.toMillis(period));
        final ScheduledTask scheduled = Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, run -> task.run(), millis, millis, TimeUnit.MILLISECONDS);
        return scheduled::cancel;
    }

    /**
     * Always scheduled: the API of 1.20.1 cannot tell whether this is the global region's thread.
     */
    @Override
    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void nextTick(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    @Override
    public void player(UUID player, Runnable task) {
        final Player online = Bukkit.getPlayer(player);
        if (online == null) return;
        if (Bukkit.isOwnedByCurrentRegion(online)) {
            task.run();
            return;
        }
        online.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    @Override
    public void playerNextTick(UUID player, Runnable task) {
        final Player online = Bukkit.getPlayer(player);
        if (online != null) online.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    /**
     * The destination's location is read on its region and the teleport starts on the moving player's.
     */
    @Override
    public CompletableFuture<Boolean> teleport(UUID moving, UUID destination) {
        final CompletableFuture<Boolean> done = new CompletableFuture<>();
        final Player from = Bukkit.getPlayer(moving);
        final Player to = Bukkit.getPlayer(destination);
        if (from == null || to == null) {
            done.complete(false);
            return done;
        }
        final Runnable left = () -> done.complete(false);
        final ScheduledTask read = to.getScheduler().run(plugin, readTask -> {
            final Location at = to.getLocation();
            final ScheduledTask move = from.getScheduler().run(plugin, moveTask -> from.teleportAsync(at)
                    .whenComplete((teleported, error) -> done.complete(error == null && Boolean.TRUE.equals(teleported))), left);
            if (move == null) left.run();
        }, left);
        if (read == null) left.run();
        return done;
    }

}
