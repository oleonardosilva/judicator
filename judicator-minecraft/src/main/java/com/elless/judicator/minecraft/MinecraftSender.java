package com.elless.judicator.minecraft;

import com.elless.judicator.platform.JudicatorPlayer;
import com.elless.judicator.platform.JudicatorSender;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;

import java.util.Optional;

/**
 * Whoever runs a command that is not a player: the console, RCON, a command block or a function. They may do
 * everything, like the console of the other platforms.
 */
final class MinecraftSender implements JudicatorSender {

    private final CommandSourceStack source;

    private MinecraftSender(CommandSourceStack source) {
        this.source = source;
    }

    static JudicatorSender of(CommandSourceStack source, MinecraftPlatform platform) {
        if (source.getPlayer() != null) {
            return platform.getPlayer(source.getPlayer().getUUID())
                    .<JudicatorSender>map(player -> player)
                    .orElseGet(() -> new MinecraftPlayer(source.getPlayer(), platform));
        }
        return new MinecraftSender(source);
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void sendMessage(Component message) {
        source.sendSystemMessage(MinecraftText.toNative(message));
    }

    @Override
    public Optional<JudicatorPlayer> asPlayer() {
        return Optional.empty();
    }
}
