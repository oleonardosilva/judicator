package com.openmc.judicator.platform;

import net.kyori.adventure.text.Component;

import java.util.Optional;

public interface JudicatorSender {

    String getName();

    boolean hasPermission(String permission);

    void sendMessage(Component message);

    Optional<JudicatorPlayer> asPlayer();

    default boolean isPlayer() {
        return asPlayer().isPresent();
    }

}
