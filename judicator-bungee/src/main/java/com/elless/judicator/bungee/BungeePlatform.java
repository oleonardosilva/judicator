package com.elless.judicator.bungee;

import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JulLoggerAdapter;
import com.elless.judicator.platform.Platform;
import com.elless.judicator.platform.PlatformTask;
import com.elless.judicator.platform.PlatformType;
import com.elless.judicator.platform.TeleportOutcome;
import com.elless.judicator.reports.PendingTeleports;
import com.elless.judicator.reports.TeleportRequest;
import com.elless.judicator.reports.TeleportResult;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BungeePlatform implements Platform {

    /** how long {@link #connect} waits for the player to get to the server (connecting plus the server's login) */
    private static final long ARRIVAL_SECONDS = 15;

    private final Plugin plugin;
    private final ProxyServer proxy;
    private final BungeeAudiences audiences;
    private final Logger logger;
    private final PendingTeleports pendingTeleports = new PendingTeleports();
    /** connections that {@link #connect} started, until the player gets to the server or the connection fails */
    private final Map<UUID, Arrival> arrivals = new ConcurrentHashMap<>();

    public BungeePlatform(Plugin plugin, BungeeAudiences audiences) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.audiences = audiences;
        this.logger = new JulLoggerAdapter(plugin.getLogger());
    }

    BungeeAudiences audiences() {
        return audiences;
    }

    static BaseComponent toBungee(Component component) {
        return new TextComponent(BungeeComponentSerializer.get().serialize(component));
    }

    /**
     * @return the IP of a connection; empty for one that is not over TCP/IP (a Unix socket)
     */
    static String hostAddress(SocketAddress address) {
        return address instanceof InetSocketAddress inet && inet.getAddress() != null ? inet.getAddress().getHostAddress() : "";
    }

    @Override
    public PlatformType getType() {
        return PlatformType.BUNGEECORD;
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
        return proxy.getConfig().isOnlineMode();
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(String username) {
        return Optional.ofNullable(proxy.getPlayer(username)).map(this::wrap);
    }

    @Override
    public Optional<JudicatorPlayer> getPlayer(UUID uuid) {
        return Optional.ofNullable(proxy.getPlayer(uuid)).map(this::wrap);
    }

    private JudicatorPlayer wrap(ProxiedPlayer player) {
        return new BungeePlayer(player, this);
    }

    @Override
    public Collection<? extends JudicatorPlayer> getOnlinePlayers() {
        return proxy.getPlayers().stream().map(this::wrap).toList();
    }

    @Override
    public List<String> matchPlayerNames(String prefix) {
        return proxy.matchPlayer(prefix).stream().map(ProxiedPlayer::getName).toList();
    }

    @Override
    public void runAsync(Runnable task) {
        proxy.getScheduler().runAsync(plugin, task);
    }

    @Override
    public void runSync(Runnable task) {
        runAsync(task);
    }

    @Override
    public PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit) {
        // first run after one period, as the Bukkit timer does
        final ScheduledTask scheduled = proxy.getScheduler().schedule(plugin, task, period, period, unit);
        return scheduled::cancel;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        runAsync(() -> proxy.getPluginManager().dispatchCommand(proxy.getConsole(), command));
    }

    @Override
    public Optional<String> serverOf(JudicatorPlayer player) {
        return Optional.ofNullable(proxy.getPlayer(player.getUniqueId()))
                .map(ProxiedPlayer::getServer)
                .map(server -> server.getInfo().getName());
    }

    @Override
    public List<String> serverNames() {
        return List.copyOf(proxy.getServers().keySet());
    }

    /**
     * The {@code priorities} of the listeners in config.yml, in order (usually there is one listener).
     */
    @Override
    public List<String> fallbackServers() {
        return proxy.getConfig().getListeners().stream()
                .map(ListenerInfo::getServerPriority)
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    @Override
    public CompletableFuture<Boolean> connect(JudicatorPlayer player, String target) {
        final ProxiedPlayer online = proxy.getPlayer(player.getUniqueId());
        final ServerInfo server = proxy.getServerInfo(target);
        if (online == null || server == null) return CompletableFuture.completedFuture(false);
        return connect(online, server);
    }

    /**
     * BungeeCord's callback only says the connection to the server opened (the server can still refuse the login), so
     * the future completes when the player is there ({@link #onArrival}), or false when the connection fails, the
     * server kicks them while they connect, they leave, or after {@link #ARRIVAL_SECONDS} seconds.
     */
    private CompletableFuture<Boolean> connect(ProxiedPlayer player, ServerInfo server) {
        final UUID uuid = player.getUniqueId();
        final Arrival arrival = new Arrival(server.getName(), new CompletableFuture<>());
        final Arrival previous = arrivals.put(uuid, arrival);
        if (previous != null) previous.done().complete(false);
        player.connect(server, (opened, error) -> {
            if (error != null || !Boolean.TRUE.equals(opened)) arrival.done().complete(false);
        }, ServerConnectEvent.Reason.PLUGIN);
        return arrival.done().completeOnTimeout(false, ARRIVAL_SECONDS, TimeUnit.SECONDS)
                .whenComplete((arrived, error) -> arrivals.remove(uuid, arrival));
    }

    private record Arrival(String server, CompletableFuture<Boolean> done) {
    }

    /**
     * The player got to a server ({@code ServerSwitchEvent}).
     */
    void onArrival(ProxiedPlayer player, ServerInfo server) {
        endArrival(player, server, true);
    }

    /**
     * A server refused the player while they were connecting to it ({@code ServerKickEvent}).
     */
    void onConnectionRefused(ProxiedPlayer player, ServerInfo server) {
        endArrival(player, server, false);
    }

    void onLeave(ProxiedPlayer player) {
        final Arrival arrival = arrivals.get(player.getUniqueId());
        if (arrival != null) arrival.done().complete(false);
    }

    private void endArrival(ProxiedPlayer player, ServerInfo server, boolean arrived) {
        final Arrival arrival = arrivals.get(player.getUniqueId());
        if (arrival != null && arrival.server().equals(server.getName())) arrival.done().complete(arrived);
    }

    @Override
    public CompletableFuture<TeleportOutcome> teleport(JudicatorPlayer staff, JudicatorPlayer target) {
        final ProxiedPlayer moving = proxy.getPlayer(staff.getUniqueId());
        final ProxiedPlayer destination = proxy.getPlayer(target.getUniqueId());
        if (moving == null || destination == null || destination.getServer() == null) {
            return CompletableFuture.completedFuture(TeleportOutcome.FAILED);
        }
        final ServerInfo there = destination.getServer().getInfo();
        final boolean alreadyThere = moving.getServer() != null && moving.getServer().getInfo().equals(there);
        final CompletableFuture<Boolean> arrived = alreadyThere ? CompletableFuture.completedFuture(true) : connect(moving, there);
        return arrived.thenCompose(connected -> {
            final Server connection = moving.getServer();
            if (!connected || connection == null) return CompletableFuture.completedFuture(TeleportOutcome.FAILED);
            // the backend answers with a TeleportResult (onTeleportResult); a backend without Judicator never does
            return pendingTeleports.request(staff.getUniqueId(), target.getUniqueId(),
                    request -> connection.sendData(TeleportRequest.CHANNEL, request));
        });
    }

    /**
     * A backend answered a teleport request through the staff member's connection.
     *
     * @param from the player the backend connection belongs to
     */
    void onTeleportResult(ProxiedPlayer from, TeleportResult result) {
        pendingTeleports.answer(from.getUniqueId(), result);
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        final ProxiedPlayer online = proxy.getPlayer(player.getUniqueId());
        if (online == null || online.getServer() == null) return;
        online.getServer().getInfo().getPlayers()
                .forEach(recipient -> audiences.player(recipient).sendMessage(message.apply(wrap(recipient))));
    }

}
