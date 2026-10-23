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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

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
                connectionHandler.onLoginAllowed(username, address);
                connectionHandler.onJoin(new VelocityPlayer(player));
            }
        });
    }

    @Subscribe
    public EventTask onDisconnect(DisconnectEvent event) {
        // a second connection refused because the name is already online: the player who stays is not leaving
        if (event.getLoginStatus() == DisconnectEvent.LoginStatus.CONFLICTING_LOGIN) return null;
        final Player player = event.getPlayer();
        final String username = player.getUsername();
        final String address = player.getRemoteAddress().getAddress().getHostAddress();
        // pausing an online-only mute writes to the database
        return EventTask.async(() -> connectionHandler.onDisconnect(username, address));
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
