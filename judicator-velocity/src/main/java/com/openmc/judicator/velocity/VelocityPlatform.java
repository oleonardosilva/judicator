package com.openmc.judicator.velocity;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import com.openmc.judicator.platform.TeleportOutcome;
import com.openmc.judicator.reports.TeleportRequest;
import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.Platform;
import com.openmc.judicator.platform.PlatformTask;
import com.openmc.judicator.platform.PlatformType;
import com.openmc.judicator.reports.TeleportResult;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class VelocityPlatform implements Platform {

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    static final MinecraftChannelIdentifier TELEPORT_CHANNEL = MinecraftChannelIdentifier.from(TeleportRequest.CHANNEL);
    private static final long TELEPORT_ANSWER_SECONDS = 5;
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final AtomicLong nextTeleport = new AtomicLong();

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
    public Optional<JudicatorPlayer> getPlayer(UUID uuid) {
        return server.getPlayer(uuid).map(VelocityPlayer::new);
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
        // first run after one period, as the Bukkit timer does
        final ScheduledTask scheduled = server.getScheduler().buildTask(plugin, task)
                .delay(period, unit)
                .repeat(period, unit)
                .schedule();
        return scheduled::cancel;
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public Optional<String> serverOf(JudicatorPlayer player) {
        return server.getPlayer(player.getUniqueId())
                .flatMap(Player::getCurrentServer)
                .map(connection -> connection.getServerInfo().getName());
    }

    @Override
    public List<String> serverNames() {
        return server.getAllServers().stream().map(registered -> registered.getServerInfo().getName()).toList();
    }

    @Override
    public List<String> fallbackServers() {
        return List.copyOf(server.getConfiguration().getAttemptConnectionOrder());
    }

    @Override
    public CompletableFuture<Boolean> connect(JudicatorPlayer player, String target) {
        final Optional<Player> online = server.getPlayer(player.getUniqueId());
        final Optional<RegisteredServer> registered = server.getServer(target);
        if (online.isEmpty() || registered.isEmpty()) return CompletableFuture.completedFuture(false);
        return online.get().createConnectionRequest(registered.get()).connect()
                .thenApply(ConnectionRequestBuilder.Result::isSuccessful)
                .exceptionally(error -> false);
    }

    @Override
    public CompletableFuture<TeleportOutcome> teleport(JudicatorPlayer staff, JudicatorPlayer target) {
        final Optional<Player> moving = server.getPlayer(staff.getUniqueId());
        final Optional<ServerConnection> destination = server.getPlayer(target.getUniqueId()).flatMap(Player::getCurrentServer);
        if (moving.isEmpty() || destination.isEmpty()) return CompletableFuture.completedFuture(TeleportOutcome.FAILED);
        final RegisteredServer there = destination.get().getServer();
        final boolean alreadyThere = moving.get().getCurrentServer().map(current -> current.getServer().equals(there)).orElse(false);
        final CompletableFuture<Boolean> arrived = alreadyThere ? CompletableFuture.completedFuture(true)
                : moving.get().createConnectionRequest(there).connect().thenApply(ConnectionRequestBuilder.Result::isSuccessful)
                .exceptionally(error -> false);
        return arrived.thenCompose(connected -> {
            if (!connected) return CompletableFuture.completedFuture(TeleportOutcome.FAILED);
            final Optional<ServerConnection> connection = moving.get().getCurrentServer();
            if (connection.isEmpty()) return CompletableFuture.completedFuture(TeleportOutcome.FAILED);
            // the backend answers with a TeleportResult (onTeleportResult); a backend without Judicator never does
            final PendingTeleport pending = new PendingTeleport(nextTeleport.incrementAndGet(), new CompletableFuture<>());
            final PendingTeleport previous = pendingTeleports.put(staff.getUniqueId(), pending);
            if (previous != null) previous.answer().complete(TeleportOutcome.SUPERSEDED);
            connection.get().sendPluginMessage(TELEPORT_CHANNEL,
                    new TeleportRequest(pending.id(), staff.getUniqueId(), target.getUniqueId()).encode());
            return pending.answer().completeOnTimeout(TeleportOutcome.SERVER_ONLY, TELEPORT_ANSWER_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((outcome, error) -> pendingTeleports.remove(staff.getUniqueId(), pending));
        });
    }

    /**
     * A backend answered a teleport request through the staff member's connection.
     *
     * @param from the player the backend connection belongs to
     */
    void onTeleportResult(Player from, TeleportResult result) {
        if (!from.getUniqueId().equals(result.staff())) return;
        final PendingTeleport pending = pendingTeleports.get(result.staff());
        // an answer to an older request is not this one's
        if (pending != null && pending.id() == result.request()) {
            pending.answer().complete(result.teleported() ? TeleportOutcome.TELEPORTED : TeleportOutcome.FAILED);
        }
    }

    private record PendingTeleport(long id, CompletableFuture<TeleportOutcome> answer) {
    }

    @Override
    public void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message) {
        server.getPlayer(player.getUniqueId())
                .flatMap(Player::getCurrentServer)
                .ifPresent(connection -> connection.getServer().getPlayersConnected()
                        .forEach(recipient -> recipient.sendMessage(message.apply(new VelocityPlayer(recipient)))));
    }

}
