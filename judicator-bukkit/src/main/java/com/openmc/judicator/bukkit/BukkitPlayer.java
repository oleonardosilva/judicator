package com.openmc.judicator.bukkit;

import com.openmc.judicator.platform.JudicatorPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.UUID;

public class BukkitPlayer implements JudicatorPlayer {

    private final Player handle;
    private final BukkitPlatform platform;
    private final String hostAddress;

    BukkitPlayer(Player handle, BukkitPlatform platform) {
        this.handle = handle;
        this.platform = platform;
        final InetSocketAddress address = handle.getAddress();
        this.hostAddress = address == null ? "" : address.getAddress().getHostAddress();
    }

    boolean isHandle(Player player) {
        return handle == player;
    }

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
    public UUID getUniqueId() {
        return handle.getUniqueId();
    }

    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    @Override
    public void disconnect(Component reason) {
        final String legacyReason = LegacyComponentSerializer.legacySection().serialize(reason);
        platform.runSync(() -> handle.kickPlayer(legacyReason));
    }

}
