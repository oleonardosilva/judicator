package com.openmc.judicator.velocity;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.Optional;

public record VelocitySender(CommandSource handle) implements JudicatorSender {

    public static JudicatorSender of(CommandSource source) {
        return source instanceof Player player ? new VelocityPlayer(player) : new VelocitySender(source);
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
        handle.sendMessage(message);
    }

    @Override
    public Optional<JudicatorPlayer> asPlayer() {
        return Optional.empty();
    }

}
