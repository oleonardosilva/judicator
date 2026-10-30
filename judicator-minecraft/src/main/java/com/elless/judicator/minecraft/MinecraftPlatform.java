package com.elless.judicator.minecraft;

import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.platform.PlatformTask;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.platform.TeleportOutcome;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A Minecraft 26.1+ server with a mod loader: one game thread (the server's), the players online, the console. The
 * loader module gives the type, where the files go and how permissions are checked.
 */
public class MinecraftPlatform implements Platform {

    private final PlatformType type;
    private final Path dataDirectory;
    private final MinecraftPermissions permissions;
    private final Logger logger = LoggerFactory.getLogger("Judicator");
    private final Map<UUID, MinecraftPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final ExecutorService async = Executors.newCachedThreadPool(threads("Judicator Async"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(threads("Judicator Timer"));
    private volatile MinecraftServer server;
    private volatile ClassLoader databaseDrivers;

    public MinecraftPlatform(PlatformType type, Path dataDirectory, MinecraftPermissions permissions) {
        this.type = type;
        this.dataDirectory = dataDirectory;
        this.permissions = permissions;
    }

    private static ThreadFactory threads(String name) {
        final AtomicInteger count = new AtomicInteger();
        return task -> {
            final Thread thread = new Thread(task, name + " #" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    void server(MinecraftServer server) {
        this.server = server;
    }

    MinecraftServer server() {
        final MinecraftServer current = server;
        if (current == null) throw new IllegalStateException("the server is not running");
        return current;
    }

    MinecraftPermissions permissions() {
        return permissions;
    }

    void databaseDrivers(ClassLoader drivers) {
        this.databaseDrivers = drivers;
    }

    void shutdown() {
        timer.shutdownNow();
        async.shutdown();
        try {
            async.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The threads of {@link #runAsync}, for a loader that waits on a future (a login).
     */
    public ExecutorService asyncExecutor() {
        return async;
    }

    MinecraftPlayer track(ServerPlayer player) {
        final MinecraftPlayer tracked = new MinecraftPlayer(player, this);
        onlinePlayers.put(player.getUUID(), tracked);
        return tracked;
    }

    /**
     * @return the player as tracked, unless a newer connection of the same account replaced it
     */
    Optional<MinecraftPlayer> untrack(ServerPlayer player) {
        final MinecraftPlayer[] removed = new MinecraftPlayer[1];
        onlinePlayers.computeIfPresent(player.getUUID(), (uuid, tracked) -> {
            if (tracked.handle() != player) return tracked;
            removed[0] = tracked;
            return null;
        });
        return Optional.ofNullable(removed[0]);
    }

    @Override
    public Optional<ClassLoader> databaseDrivers() {
        return Optional.ofNullable(databaseDrivers);
    }

    @Override
    public PlatformType getType() {
        return type;
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
        return server().usesAuthentication();
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(String username) {
        return onlinePlayers.values().stream()
                .filter(player -> player.getName().equalsIgnoreCase(username))
                .<JudicatorPlayer>map(player -> player)
                .findFirst();
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(UUID uuid) {
        return Optional.ofNullable(onlinePlayers.get(uuid));
    }

    @Override
    public Collection<? extends JudicatorPlayer> getOnlinePlayers() {
        return List.copyOf(onlinePlayers.values());
    }

    @Override
    public List<String> matchPlayerNames(String prefix) {
        final String lower = prefix.toLowerCase();
        final List<String> names = onlinePlayers.values().stream().map(MinecraftPlayer::getName).toList();
        final Optional<String> exact = names.stream().filter(name -> name.equalsIgnoreCase(prefix)).findFirst();
        if (exact.isPresent()) return List.of(exact.get());
        return names.stream().filter(name -> name.toLowerCase().startsWith(lower)).toList();
    }

    @Override
    public void runAsync(Runnable task) {
        async.execute(task);
    }

    @Override
    public void runSync(Runnable task) {
        final MinecraftServer current = server();
        if (current.isSameThread()) {
            task.run();
            return;
        }
        current.execute(task);
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        final ScheduledFuture<?> scheduled = timer.scheduleAtFixedRate(() -> async.execute(task), period, period, unit);
        return () -> scheduled.cancel(false);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        runSync(() -> server().getCommands().performPrefixedCommand(server().createCommandSourceStack(), command));
    }

    @Override
    public Optional<String> serverOf(JudicatorPlayer player) {
        return Optional.empty();
    }

    @Override
    public List<String> serverNames() {
        return List.of();
    }

    @Override
    public List<String> fallbackServers() {
        return List.of();
    }

    @Override
    public CompletableFuture<Boolean> connect(JudicatorPlayer player, String server) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<TeleportOutcome> teleport(JudicatorPlayer staff, JudicatorPlayer target) {
        final CompletableFuture<TeleportOutcome> done = new CompletableFuture<>();
        runSync(() -> {
            final ServerPlayer moving = server().getPlayerList().getPlayer(staff.getUniqueId());
            final ServerPlayer destination = server().getPlayerList().getPlayer(target.getUniqueId());
            final boolean teleported = moving != null && destination != null && moving.teleportTo(destination.level(),
                    destination.getX(), destination.getY(), destination.getZ(), Set.of(), destination.getYRot(),
                    destination.getXRot(), true);
            done.complete(teleported ? TeleportOutcome.TELEPORTED : TeleportOutcome.FAILED);
        });
        return done;
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        getOnlinePlayers().forEach(recipient -> recipient.sendMessage(message.apply(recipient)));
    }
}
