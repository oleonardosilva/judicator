package com.elless.judicator.bukkit;

import com.elless.judicator.menus.MenuRenderer;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JulLoggerAdapter;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.platform.PlatformTask;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.platform.TeleportOutcome;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Function;

public class BukkitPlatform implements Platform {

    private final JavaPlugin plugin;
    private final Scheduling scheduling;
    private final BukkitAudiences audiences;
    private final Logger logger;
    private final Map<String, BukkitPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private ClassLoader databaseDrivers;
    private final BukkitMenus menus;
    private final BukkitMenuChooser chooser;
    private volatile Supplier<BukkitMenuChooser.Mode> dialogsSetting = () -> BukkitMenuChooser.Mode.MIXED;

    public BukkitPlatform(JavaPlugin plugin, Scheduling scheduling, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.scheduling = scheduling;
        this.audiences = audiences;
        this.logger = new JulLoggerAdapter(plugin.getLogger());
        this.menus = new BukkitMenus(plugin, scheduling);
        this.chooser = new BukkitMenuChooser(scheduling, menus, () -> dialogsSetting.get());
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

    Scheduling scheduling() {
        return scheduling;
    }

    BukkitAudiences audiences() {
        return audiences;
    }

    void databaseDrivers(ClassLoader drivers) {
        this.databaseDrivers = drivers;
    }

    @Override
    public Optional<ClassLoader> databaseDrivers() {
        return Optional.ofNullable(databaseDrivers);
    }

    BukkitMenus menuRenderer() {
        return menus;
    }

    BukkitMenuChooser menuChooser() {
        return chooser;
    }

    /**
     * {@code menus.dialogs} of config.yml, read on every menu opened (so a reload applies it).
     */
    void dialogsSetting(Supplier<BukkitMenuChooser.Mode> setting) {
        this.dialogsSetting = setting;
    }

    @Override
    public Optional<MenuRenderer> menus() {
        return Optional.of(chooser);
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
    public Optional<JudicatorPlayer> getPlayer(UUID uuid) {
        return onlinePlayers.values().stream().filter(player -> player.getUniqueId().equals(uuid))
                .<JudicatorPlayer>map(player -> player).findFirst();
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
        scheduling.async(task);
    }

    @Override
    public void runSync(Runnable task) {
        scheduling.global(task);
    }

    @Override
    public void runFor(JudicatorPlayer player, Runnable task) {
        scheduling.player(player.getUniqueId(), task);
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        return scheduling.asyncRepeating(task, period, unit);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        scheduling.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
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
        return scheduling.teleport(staff.getUniqueId(), target.getUniqueId())
                .thenApply(teleported -> teleported ? TeleportOutcome.TELEPORTED : TeleportOutcome.FAILED);
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        getOnlinePlayers().forEach(recipient -> recipient.sendMessage(message.apply(recipient)));
    }

}
