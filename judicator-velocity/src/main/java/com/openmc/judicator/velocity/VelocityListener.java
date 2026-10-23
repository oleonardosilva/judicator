package com.openmc.judicator.velocity;

import com.openmc.judicator.Judicator;
import com.openmc.judicator.punish.listeners.ChatHandler;
import com.openmc.judicator.punish.listeners.ConnectionHandler;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import java.util.UUID;


public class VelocityListener {

    private final Judicator judicator;
    private final ProxyServer server;
    private final ConnectionHandler connectionHandler;
    private final ChatHandler chatHandler;

    public VelocityListener(Judicator judicator, ProxyServer server) {
        this.judicator = judicator;
        this.server = server;
        this.connectionHandler = judicator.getConnectionHandler();
        this.chatHandler = judicator.getChatHandler();
    }

    @Subscribe(priority = -10)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            if (!event.getResult().isAllowed()) return;
            final String username = event.getUsername();
            final String address = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
            connectionHandler.checkPreLogin(username, address)
                    .ifPresent(kick -> event.setResult(PreLoginEvent.PreLoginComponentResult.denied(kick)));
        });
    }

    @Subscribe(priority = -10)
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> {
            if (!event.getResult().isAllowed()) return;
            final Player player = event.getPlayer();
            final String username = player.getUsername();
            final String address = player.getRemoteAddress().getAddress().getHostAddress();
            connectionHandler.checkLogin(player.getUniqueId(), username, address)
                    .ifPresent(kick -> event.setResult(ResultedEvent.ComponentResult.denied(kick)));
            if (event.getResult().isAllowed()) {
                connectionHandler.onLoginAllowed(player.getUniqueId(), username, address);
                connectionHandler.onJoin(new VelocityPlayer(player));
            }
        });
    }

    /**
     * A ban for the server chosen at login sends the player to the first server of the try list it does not cover;
     * with none left, they are disconnected with the ban screen.
     */
    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        final Optional<RegisteredServer> chosen = event.getInitialServer();
        if (chosen.isEmpty()) return null;
        return EventTask.async(() -> {
            final VelocityPlayer player = new VelocityPlayer(event.getPlayer());
            final ConnectionHandler.InitialServer decision = connectionHandler.chooseInitialServer(player, chosen.get().getServerInfo().getName());
            if (decision.kick() != null) {
                event.getPlayer().disconnect(decision.kick());
            } else if (!decision.server().equals(chosen.get().getServerInfo().getName())) {
                server.getServer(decision.server()).ifPresent(event::setInitialServer);
            }
        });
    }

    /**
     * A ban for the server a player is connecting to keeps them where they are. A connection the proxy makes on its own
     * before the player is on a server (the chosen server is down and it tries the next one) goes to the first server
     * of the try list no ban covers instead, or the player is disconnected.
     */
    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        if (!event.getResult().isAllowed()) return null;
        final Optional<RegisteredServer> target = event.getResult().getServer();
        if (target.isEmpty()) return null;
        final String name = target.get().getServerInfo().getName();
        return EventTask.async(() -> {
            final VelocityPlayer player = new VelocityPlayer(event.getPlayer());
            if (event.getPreviousServer() == null) {
                final ConnectionHandler.InitialServer decision = connectionHandler.chooseInitialServer(player, name);
                if (decision.kick() != null) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    event.getPlayer().disconnect(decision.kick());
                } else if (!decision.server().equals(name)) {
                    server.getServer(decision.server()).ifPresent(other -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(other)));
                }
                return;
            }
            connectionHandler.checkServerSwitch(player, name).ifPresent(message -> {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                event.getPlayer().sendMessage(message);
            });
        });
    }

    @Subscribe
    public EventTask onDisconnect(DisconnectEvent event) {
        // a second connection refused because the name is already online: the player who stays is not leaving
        if (event.getLoginStatus() == DisconnectEvent.LoginStatus.CONFLICTING_LOGIN) return null;
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String username = player.getUsername();
        final String address = player.getRemoteAddress().getAddress().getHostAddress();
        // pausing an online-only mute writes to the database
        return EventTask.async(() -> connectionHandler.onDisconnect(uuid, username, address));
    }

    /**
     * {@code chat-on-backends: true}: the backends (Judicator in {@code proxy-mode}) block muted chat and the commands
     * they run, so the proxy does not deny signed chat messages (a 1.19.1+ client is disconnected when the proxy does).
     * Commands of the proxy itself never reach a backend: the proxy still blocks those.
     */
    private boolean chatOnBackends() {
        return judicator.getConfig().node("chat-on-backends").getBoolean(false);
    }

    @Subscribe(priority = 10)
    public void onMutedChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed() || chatOnBackends()) return;
        final VelocityPlayer player = new VelocityPlayer(event.getPlayer());
        switch (chatHandler.chatDecision(player)) {
            case BLOCK -> event.setResult(PlayerChatEvent.ChatResult.denied());
            case SOFT -> {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(chatHandler.softMuteEcho(player, event.getMessage()));
            }
            default -> {
            }
        }
    }

    @Subscribe(priority = 10)
    public void onMutedCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player) || !event.getResult().isAllowed()) return;
        if (chatOnBackends() && !server.getCommandManager().hasCommand(event.getCommand().trim().split(" ", 2)[0])) return;
        if (chatHandler.shouldBlockCommand(new VelocityPlayer(player), event.getCommand())) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

    @Subscribe
    public void onPromptChat(PlayerChatEvent event) {
        if (chatHandler.handlePromptChat(new VelocityPlayer(event.getPlayer()), event.getMessage())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    @Subscribe
    public void onPromptCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;
        if (chatHandler.handlePromptCommand(new VelocityPlayer(player), event.getCommand())) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

}
