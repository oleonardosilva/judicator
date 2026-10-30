package com.elless.judicator.hytale;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.elless.judicator.platform.JudicatorPlayer;
import net.kyori.adventure.text.Component;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class HytalePlayer implements JudicatorPlayer {

    private final PlayerRef handle;
    private final String hostAddress;

    HytalePlayer(PlayerRef handle) {
        this.handle = handle;
        this.hostAddress = hostOf(handle.getPacketHandler().getChannel().remoteAddress());
    }

    static String hostOf(SocketAddress address) {
        return address instanceof InetSocketAddress inet && inet.getAddress() != null
                ? inet.getAddress().getHostAddress()
                : String.valueOf(address);
    }

    PlayerRef handle() {
        return handle;
    }

    @Override
    public String getName() {
        return handle.getUsername();
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    /**
     * Given on purpose, not through a wildcard ({@code *}, {@code judicator.*}) such as the operator group has: a node
     * next to it that nobody grants tells them apart.
     */
    @Override
    public boolean hasExplicitPermission(String permission) {
        if (!handle.hasPermission(permission, false)) return false;
        final int dot = permission.lastIndexOf('.');
        final String probe = (dot < 0 ? "" : permission.substring(0, dot + 1)) + "judicator-wildcard-probe";
        return !handle.hasPermission(probe, false);
    }

    @Override
    public void sendMessage(Component message) {
        handle.sendMessage(HytaleText.toMessage(message));
    }

    @Override
    public Optional<Locale> getLocale() {
        final String language = handle.getLanguage();
        if (language == null || language.isBlank()) return Optional.empty();
        return Optional.of(Locale.forLanguageTag(language.replace('_', '-')));
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUuid();
    }

    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    @Override
    public void disconnect(Component reason) {
        handle.getPacketHandler().disconnect(HytaleText.toMessage(reason));
    }
}
