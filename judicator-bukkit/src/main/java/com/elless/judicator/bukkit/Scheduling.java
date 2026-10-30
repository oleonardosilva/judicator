package com.elless.judicator.bukkit;

import com.elless.judicator.platform.PlatformTask;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Where the plugin's tasks run: the Bukkit scheduler ({@link BukkitScheduling}), or on Folia, which has no main thread
 * and no Bukkit scheduler, its region schedulers ({@code FoliaScheduling}, in the {@code folia} source set). Nothing
 * else in the plugin calls {@code Bukkit.getScheduler()}.
 */
interface Scheduling {

    /**
     * Folia when the server is one, the Bukkit scheduler otherwise.
     */
    static Scheduling of(Plugin plugin) {
        if (!BukkitCompat.classExists("io.papermc.paper.threadedregions.RegionizedServer")) return new BukkitScheduling(plugin);
        try {
            return (Scheduling) Class.forName("com.elless.judicator.bukkit.FoliaScheduling")
                    .getDeclaredConstructor(Plugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError e) {
            // the Bukkit scheduler throws on Folia: without its region schedulers nothing could run
            throw new IllegalStateException("this Folia has no region schedulers Judicator knows (" + e + ")", e);
        }
    }

    /**
     * Off the game threads.
     */
    void async(Runnable task);

    /**
     * Off the game threads every period; the first run is one period from now.
     */
    PlatformTask asyncRepeating(Runnable task, long period, TimeUnit unit);

    /**
     * On the game thread, at once when already on it; on Folia, on the global region (for the console and the server
     * as a whole).
     */
    void global(Runnable task);

    /**
     * On the game thread (Folia: the global region) on the next tick.
     */
    void nextTick(Runnable task);

    /**
     * On the thread that owns the online player (the game thread; Folia: the player's region, for their inventory,
     * their connection, a teleport), at once when already on it. On Folia it does not run once the player left.
     */
    void player(UUID player, Runnable task);

    /**
     * On the thread that owns the player on the next tick (out of the event being handled now).
     */
    void playerNextTick(UUID player, Runnable task);

    /**
     * Takes one online player to another.
     *
     * @return completes with whether the player got there (false when either one is not online)
     */
    CompletableFuture<Boolean> teleport(UUID moving, UUID destination);

}
