package com.elless.judicator.hytale;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import net.kyori.adventure.text.Component;

import java.util.Optional;

/**
 * The console, or whatever runs a command that is not a player.
 */
final class HytaleSender implements JudicatorSender {

    private final CommandSender handle;

    private HytaleSender(CommandSender handle) {
        this.handle = handle;
    }

    static JudicatorSender of(CommandSender sender, HytalePlatform platform) {
        if (sender instanceof PlayerRef player) {
            return platform.getPlayer(player.getUuid())
                    .<JudicatorSender>map(online -> online)
                    .orElseGet(() -> new HytalePlayer(player));
        }
        return new HytaleSender(sender);
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission, true);
    }

    @Override
    public void sendMessage(Component message) {
        handle.sendMessage(HytaleText.toMessage(message));
    }

    @Override
    public Optional<JudicatorPlayer> asPlayer() {
        return Optional.empty();
    }
}
