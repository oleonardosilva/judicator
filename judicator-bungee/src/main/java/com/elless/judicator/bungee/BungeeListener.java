package com.elless.judicator.bungee;

import com.elless.judicator.Judicator;
import com.elless.judicator.punish.listeners.ChatHandler;
import com.elless.judicator.punish.listeners.ConnectionHandler;
import com.elless.judicator.reports.TeleportRequest;
import com.elless.judicator.reports.TeleportResult;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * BungeeCord's events into the core, as {@code VelocityListener} does on Velocity. Login events wait for the database
 * off the network threads ({@code registerIntent}); a switch of server waits for it in place, as the event cannot.
 */
public class BungeeListener implements Listener {

    private final Plugin plugin;
    private final Judicator judicator;
    private final BungeePlatform platform;
    private final ProxyServer proxy;
    private final ConnectionHandler connectionHandler;
    private final ChatHandler chatHandler;
    /** the server {@link #onPostLogin} let each player who just logged in go to */
    private final Map<UUID, String> approvedInitialServers = new ConcurrentHashMap<>();
    /** connections the proxy makes because the player has to leave their server */
    private static final Set<ServerConnectEvent.Reason> LEAVING_REASONS = EnumSet.of(ServerConnectEvent.Reason.LOBBY_FALLBACK,
            ServerConnectEvent.Reason.SERVER_DOWN_REDIRECT, ServerConnectEvent.Reason.KICK_REDIRECT);

    BungeeListener(Plugin plugin, Judicator judicator, BungeePlatform platform) {
        this.plugin = plugin;
        this.judicator = judicator;
        this.platform = platform;
        this.proxy = plugin.getProxy();
        this.connectionHandler = judicator.getConnectionHandler();
        this.chatHandler = judicator.getChatHandler();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(PreLoginEvent event) {
        if (event.isCancelled()) return;
        final PendingConnection connection = event.getConnection();
        final String username = connection.getName();
        final String address = BungeePlatform.hostAddress(connection.getSocketAddress());
        event.registerIntent(plugin);
        platform.runAsync(() -> {
            try {
                connectionHandler.checkPreLogin(username, address).ifPresent(kick -> {
                    event.setReason(BungeePlatform.toBungee(kick));
                    event.setCancelled(true);
                });
            } catch (RuntimeException e) {
                judicator.getLogger().error("Could not check the pre-login of {}", username, e);
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(LoginEvent event) {
        if (event.isCancelled()) return;
        final PendingConnection connection = event.getConnection();
        final UUID uuid = connection.getUniqueId();
        final String username = connection.getName();
        final String address = BungeePlatform.hostAddress(connection.getSocketAddress());
        event.registerIntent(plugin);
        platform.runAsync(() -> {
            try {
                connectionHandler.checkLogin(uuid, username, address).ifPresent(kick -> {
                    event.setReason(BungeePlatform.toBungee(kick));
                    event.setCancelled(true);
                });
            } catch (RuntimeException e) {
                judicator.getLogger().error("Could not check the login of {}", username, e);
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    /**
     * The player is on the proxy (every plugin let them in): the address is recorded and paused online-only mutes
     * resume. A ban for the server chosen for them sends them to the first server of the priorities it does not cover,
     * or disconnects them.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        final ProxiedPlayer handle = event.getPlayer();
        final BungeePlayer player = new BungeePlayer(handle, platform);
        final String address = player.getHostAddress();
        final ServerInfo target = event.getTarget();
        event.registerIntent(plugin);
        platform.runAsync(() -> {
            try {
                if (target != null && chooseInitialServer(player, target.getName(), event::setTarget)) {
                    approvedInitialServers.put(player.getUniqueId(), event.getTarget().getName());
                }
            } catch (RuntimeException e) {
                judicator.getLogger().error("Could not check the first server of {}", player.getName(), e);
            } finally {
                event.completeIntent(plugin);
            }
            // as on Velocity, where this runs at login, before the first server is chosen
            connectionHandler.onLoginAllowed(player.getUniqueId(), player.getName(), address);
            connectionHandler.onJoin(player);
        });
    }

    /**
     * @param redirect takes the server to send the player to instead of {@code chosen}
     * @return false when the player was disconnected: every server they could join has a ban for it
     */
    private boolean chooseInitialServer(BungeePlayer player, String chosen, Consumer<ServerInfo> redirect) {
        final ConnectionHandler.InitialServer decision = connectionHandler.chooseInitialServer(player, chosen);
        if (decision.kick() != null) {
            player.disconnect(decision.kick());
            return false;
        }
        if (!decision.server().equals(chosen)) {
            final ServerInfo server = proxy.getServerInfo(decision.server());
            if (server != null) redirect.accept(server);
        }
        return true;
    }

    /**
     * A ban for the server a player is connecting to keeps them where they are. A connection before the player is on
     * a server goes where {@link #onPostLogin} decided; when another plugin changed it, or the proxy tries the next
     * server, it is checked as the first one. So is a connection the proxy makes because the player's server went down
     * or kicked them: they cannot stay there. The event cannot wait, so the database is read in place (as LibertyBans
     * does).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onServerConnect(ServerConnectEvent event) {
        if (event.isCancelled()) return;
        final ProxiedPlayer handle = event.getPlayer();
        final BungeePlayer player = new BungeePlayer(handle, platform);
        final String name = event.getTarget().getName();
        final boolean noServer = handle.getServer() == null;
        if (noServer && name.equals(approvedInitialServers.remove(handle.getUniqueId()))) return;
        if (noServer || LEAVING_REASONS.contains(event.getReason())) {
            // a cancelled first connection is only accepted from a player being disconnected
            if (!chooseInitialServer(player, name, event::setTarget)) event.setCancelled(true);
            return;
        }
        connectionHandler.checkServerSwitch(player, name).ifPresent(message -> {
            event.setCancelled(true);
            player.sendMessage(message);
        });
    }

    /**
     * The player got to a server: a ban for it applied on the way moves them on. The first server is also when a staff
     * member hears of the open reports (before it the client cannot get chat).
     */
    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        final Server server = event.getPlayer().getServer();
        if (server != null) platform.onArrival(event.getPlayer(), server.getInfo());
        final BungeePlayer player = new BungeePlayer(event.getPlayer(), platform);
        final boolean firstServer = event.getFrom() == null;
        platform.runAsync(() -> {
            connectionHandler.checkArrival(player);
            if (firstServer) judicator.getReportService().notifyOnJoin(player);
        });
    }

    /**
     * Only the proxy talks on Judicator's plugin channel ({@code /reports tp}): what a client sends on it is never
     * passed on, so no player can make a backend teleport them; what a backend sends is its answer to a teleport.
     */
    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(TeleportRequest.CHANNEL)) return;
        event.setCancelled(true);
        if (event.getSender() instanceof Server && event.getReceiver() instanceof ProxiedPlayer staff) {
            TeleportResult.decode(event.getData()).ifPresent(result -> platform.onTeleportResult(staff, result));
        }
    }

    /**
     * A server refused a player connecting to it (whitelist, full...): a connection Judicator started failed.
     */
    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        if (event.getState() == ServerKickEvent.State.CONNECTING) {
            platform.onConnectionRefused(event.getPlayer(), event.getKickedFrom());
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        final ProxiedPlayer handle = event.getPlayer();
        final UUID uuid = handle.getUniqueId();
        approvedInitialServers.remove(uuid);
        platform.onLeave(handle);
        final String username = handle.getName();
        final String address = BungeePlatform.hostAddress(handle.getSocketAddress());
        // pausing an online-only mute writes to the database
        platform.runAsync(() -> {
            // the same account logged in again and took over this connection: the player who stays is not leaving
            final ProxiedPlayer current = proxy.getPlayer(uuid);
            if (current != null && current != handle) return;
            connectionHandler.onDisconnect(uuid, username, address);
        });
    }

    /**
     * {@code chat-on-backends: true}: the backends (Judicator in {@code proxy-mode}) block muted chat and the commands
     * they run, so the proxy does not cancel signed chat messages (a backend can disconnect a 1.19.1+ client whose
     * chat chain skips one). Commands of the proxy itself never reach a backend: the proxy still blocks those.
     */
    private boolean chatOnBackends() {
        return judicator.getConfig().node("chat-on-backends").getBoolean(false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMutedChat(ChatEvent event) {
        if (event.isCancelled() || !(event.getSender() instanceof ProxiedPlayer handle)) return;
        final BungeePlayer player = new BungeePlayer(handle, platform);
        if (event.isCommand()) {
            if (chatOnBackends() && !event.isProxyCommand()) return;
            if (chatHandler.shouldBlockCommand(player, stripSlash(event.getMessage()))) event.setCancelled(true);
            return;
        }
        if (chatOnBackends()) return;
        switch (chatHandler.chatDecision(player)) {
            case BLOCK -> event.setCancelled(true);
            case SOFT -> {
                event.setCancelled(true);
                player.sendMessage(chatHandler.softMuteEcho(player, event.getMessage()));
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onPromptChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer handle)) return;
        final BungeePlayer player = new BungeePlayer(handle, platform);
        final boolean handled = event.isCommand()
                ? chatHandler.handlePromptCommand(player, stripSlash(event.getMessage()))
                : chatHandler.handlePromptChat(player, event.getMessage());
        if (handled) event.setCancelled(true);
    }

    private static String stripSlash(String message) {
        return message.startsWith("/") ? message.substring(1) : message;
    }

}
