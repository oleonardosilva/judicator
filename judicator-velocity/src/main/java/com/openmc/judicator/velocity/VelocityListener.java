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

public class VelocityListener {

    private final ConnectionHandler connectionHandler;
    private final ChatHandler chatHandler;

    public VelocityListener(Judicator judicator) {
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
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        final Player player = event.getPlayer();
        connectionHandler.onDisconnect(player.getUsername(), player.getRemoteAddress().getAddress().getHostAddress());
    }

    @Subscribe(priority = 10)
    public void onMutedChat(PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) return;
        if (chatHandler.shouldBlockChat(new VelocityPlayer(event.getPlayer()))) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    @Subscribe(priority = 10)
    public void onMutedCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player) || !event.getResult().isAllowed()) return;
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
