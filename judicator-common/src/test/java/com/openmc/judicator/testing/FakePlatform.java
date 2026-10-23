package com.openmc.judicator.testing;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.platform.PlatformType;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * {@link Platform} for tests: online players are whatever the test adds, tasks run immediately on the calling
 * thread, announcements go to every online player and console commands are recorded instead of run.
 */
public final class FakePlatform implements Platform {

    private final Path dataDirectory;
    private final boolean onlineMode;
    private final PlatformType type;
    private final Map<String, FakePlayer> players = new LinkedHashMap<>();
    private final List<String> consoleCommands = new ArrayList<>();
    private final List<Component> announcements = new ArrayList<>();
    private final List<Runnable> repeating = new ArrayList<>();
    /** proxy servers, in the order of velocity.toml's try list */
    private final List<String> servers = new ArrayList<>();
    private final List<String> fallbacks = new ArrayList<>();
    private final Map<UUID, String> serverOfPlayer = new HashMap<>();
    /** servers that refuse connections (down, full) */
    private final Set<String> refusing = new HashSet<>();

    public FakePlatform(Path dataDirectory, boolean onlineMode) {
        this(dataDirectory, onlineMode, PlatformType.VELOCITY);
    }

    public FakePlatform(Path dataDirectory, boolean onlineMode, PlatformType type) {
        this.dataDirectory = dataDirectory;
        this.onlineMode = onlineMode;
        this.type = type;
    }

    public static FakePlatform create(Path dataDirectory, boolean onlineMode) {
        return new FakePlatform(dataDirectory, onlineMode);
    }

    public void add(FakePlayer player) {
        players.put(player.getName().toLowerCase(Locale.ROOT), player);
    }

    public void remove(FakePlayer player) {
        players.remove(player.getName().toLowerCase(Locale.ROOT));
        serverOfPlayer.remove(player.getUniqueId());
    }

    /**
     * Proxy servers of the network; the first ones listed in {@code fallbacks} form velocity.toml's try list.
     */
    public FakePlatform servers(List<String> names, List<String> tryList) {
        servers.clear();
        servers.addAll(names);
        fallbacks.clear();
        fallbacks.addAll(tryList);
        return this;
    }

    /**
     * Puts an online player on a server of the proxy (as if they connected to it).
     */
    public void moveTo(FakePlayer player, String server) {
        serverOfPlayer.put(player.getUniqueId(), server);
    }

    public void refuseConnections(String server) {
        refusing.add(server);
    }

    public List<String> consoleCommands() {
        return consoleCommands;
    }

    public List<String> announcements() {
        return announcements.stream().map(Texts::plain).toList();
    }

    @Override
    public PlatformType getType() {
        return type;
    }

    @Override
    public Logger getLogger() {
        return NOPLogger.NOP_LOGGER;
    }

    @Override
    public Path getDataDirectory() {
        return dataDirectory;
    }

    @Override
    public boolean isOnlineMode() {
        return onlineMode;
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(String username) {
        return Optional.ofNullable(players.get(username.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(UUID uuid) {
        return players.values().stream().filter(player -> player.getUniqueId().equals(uuid))
                .<JudicatorPlayer>map(player -> player).findFirst();
    }

    @Override
    public Collection<? extends JudicatorPlayer> getOnlinePlayers() {
        return List.copyOf(players.values());
    }

    @Override
    public List<String> matchPlayerNames(String prefix) {
        return players.values().stream().map(FakePlayer::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }

    @Override
    public void runAsync(Runnable task) {
        task.run();
    }

    @Override
    public void runSync(Runnable task) {
        task.run();
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        repeating.add(task);
        return () -> repeating.remove(task);
    }

    /**
     * Runs once every repeating task still scheduled (as if their period went by).
     */
    public void runRepeatingTasks() {
        List.copyOf(repeating).forEach(Runnable::run);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        consoleCommands.add(command);
    }

    @Override
    public Optional<String> serverOf(JudicatorPlayer player) {
        return Optional.ofNullable(serverOfPlayer.get(player.getUniqueId()));
    }

    @Override
    public List<String> serverNames() {
        return List.copyOf(servers);
    }

    @Override
    public List<String> fallbackServers() {
        return List.copyOf(fallbacks);
    }

    @Override
    public CompletableFuture<Boolean> connect(JudicatorPlayer player, String server) {
        if (!servers.contains(server) || refusing.contains(server) || getPlayer(player.getUniqueId()).isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        serverOfPlayer.put(player.getUniqueId(), server);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        announcements.add(message.apply(player));
        List.copyOf(players.values()).forEach(online -> online.sendMessage(message.apply(online)));
    }
}
