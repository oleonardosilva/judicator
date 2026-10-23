package com.openmc.judicator.bungee;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;

public record BungeeSender(CommandSender handle, BungeePlatform platform) implements JudicatorSender {

    public static JudicatorSender of(CommandSender sender, BungeePlatform platform) {
        return sender instanceof ProxiedPlayer player ? new BungeePlayer(player, platform) : new BungeeSender(sender, platform);
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public void sendMessage(Component message) {
        platform.audiences().sender(handle).sendMessage(message);
    }

    @Override
    public Optional<JudicatorPlayer> asPlayer() {
        return Optional.empty();
    }

}
