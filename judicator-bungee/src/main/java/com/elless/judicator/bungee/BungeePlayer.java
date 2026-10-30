package com.elless.judicator.bungee;

import com.elless.judicator.platform.JudicatorPlayer;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record BungeePlayer(ProxiedPlayer handle, BungeePlatform platform) implements JudicatorPlayer {

    @Override
    public String getName() {
        return handle.getName();
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public void sendMessage(Component message) {
        platform.audiences().player(handle).sendMessage(message);
    }

    @Override
    public Optional<Locale> getLocale() {
        // null until the client sends its settings
        return Optional.ofNullable(handle.getLocale());
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUniqueId();
    }

    @Override
    public String getHostAddress() {
        return BungeePlatform.hostAddress(handle.getSocketAddress());
    }

    @Override
    public void disconnect(Component reason) {
        handle.disconnect(BungeePlatform.toBungee(reason));
    }

}
