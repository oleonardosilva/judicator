package com.elless.judicator.minecraft;

import com.elless.judicator.platform.JudicatorPlayer;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class MinecraftPlayer implements JudicatorPlayer {

    private final ServerPlayer handle;
    private final MinecraftPlatform platform;
    private final String hostAddress;

    MinecraftPlayer(ServerPlayer handle, MinecraftPlatform platform) {
        this.handle = handle;
        this.platform = platform;
        this.hostAddress = handle.getIpAddress();
    }

    ServerPlayer handle() {
        return handle;
    }

    @Override
    public String getName() {
        return handle.getGameProfile().name();
    }

    @Override
    public boolean hasPermission(String permission) {
        return platform.permissions().has(handle, permission);
    }

    @Override
    public boolean hasExplicitPermission(String permission) {
        return platform.permissions().explicit(handle, permission);
    }

    @Override
    public void sendMessage(Component message) {
        handle.sendSystemMessage(MinecraftText.toNative(message));
    }

    @Override
    public Optional<Locale> getLocale() {
        // "pt_br" (vanilla) or whatever a resource pack uses
        final String language = handle.clientInformation().language();
        if (language == null || language.isBlank()) return Optional.empty();
        return Optional.of(Locale.forLanguageTag(language.replace('_', '-')));
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUUID();
    }

    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    @Override
    public void disconnect(Component reason) {
        final net.minecraft.network.chat.Component text = MinecraftText.toNative(reason);
        platform.runSync(() -> handle.connection.disconnect(text));
    }
}
