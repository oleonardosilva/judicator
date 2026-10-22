package com.openmc.judicator.velocity;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public record VelocityPlayer(Player handle) implements JudicatorPlayer {

    @Override
    public String getName() {
        return handle.getUsername();
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public void sendMessage(Component message) {
        handle.sendMessage(message);
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUniqueId();
    }

    @Override
    public String getHostAddress() {
        return handle.getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public void disconnect(Component reason) {
        handle.disconnect(reason);
    }

}
