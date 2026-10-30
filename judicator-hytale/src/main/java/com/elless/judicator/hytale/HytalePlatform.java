package com.elless.judicator.hytale;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JulLoggerAdapter;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.platform.PlatformTask;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.platform.TeleportOutcome;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * A Hytale server: each world ticks on a thread of its own and messages, kicks and commands are safe from any thread,
 * so "sync" work runs on Judicator's own threads, like on the proxies. Players are authenticated by Hytale.
 */
final class HytalePlatform implements Platform {

    private final Path dataDirectory;
    private final Logger logger;
    private final Map<UUID, HytalePlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final ExecutorService async = Executors.newCachedThreadPool(threads("Judicator Async"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(threads("Judicator Timer"));
    private volatile ClassLoader databaseDrivers;

    HytalePlatform(Path dataDirectory, java.util.logging.Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = new JulLoggerAdapter(logger);
    }

    private static ThreadFactory threads(String name) {
        final AtomicInteger count = new AtomicInteger();
        return task -> {
            final Thread thread = new Thread(task, name + " #" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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

    HytalePlayer track(PlayerRef player) {
        final HytalePlayer tracked = new HytalePlayer(player);
        onlinePlayers.put(player.getUuid(), tracked);
        return tracked;
    }

    Optional<HytalePlayer> untrack(PlayerRef player) {
        final HytalePlayer[] removed = new HytalePlayer[1];
        onlinePlayers.computeIfPresent(player.getUuid(), (uuid, tracked) -> {
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
        return PlatformType.HYTALE;
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
        return true;
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
        final List<String> names = onlinePlayers.values().stream().map(HytalePlayer::getName).toList();
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
        async.execute(task);
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        final ScheduledFuture<?> scheduled = timer.scheduleAtFixedRate(() -> async.execute(task), period, period, unit);
        return () -> scheduled.cancel(false);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command);
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

    /**
     * Hytale's own {@code /tp (player) (target)}, run by the console.
     */
    @Override
    public CompletableFuture<TeleportOutcome> teleport(JudicatorPlayer staff, JudicatorPlayer target) {
        return CommandManager.get().handleCommand(ConsoleSender.INSTANCE, "tp " + staff.getName() + " " + target.getName())
                .handle((done, error) -> error == null ? TeleportOutcome.TELEPORTED : TeleportOutcome.FAILED);
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        getOnlinePlayers().forEach(recipient -> recipient.sendMessage(message.apply(recipient)));
    }
}
