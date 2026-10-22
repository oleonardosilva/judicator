package com.openmc.judicator.bukkit;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class BukkitSender implements JudicatorSender {

    private final CommandSender handle;
    private final BukkitPlatform platform;

    private BukkitSender(CommandSender handle, BukkitPlatform platform) {
        this.handle = handle;
        this.platform = platform;
    }

    static JudicatorSender of(CommandSender sender, BukkitPlatform platform) {
        if (sender instanceof Player player) {
            return platform.getPlayer(player.getName())
                    .<JudicatorSender>map(p -> p)
                    .orElseGet(() -> new BukkitPlayer(player, platform));
        }
        return new BukkitSender(sender, platform);
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
