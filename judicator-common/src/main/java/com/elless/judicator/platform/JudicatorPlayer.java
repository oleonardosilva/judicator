package com.elless.judicator.platform;

import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public interface JudicatorPlayer extends JudicatorSender {

    UUID getUniqueId();

    String getHostAddress();

    void disconnect(Component reason);

    @Override
    default Optional<JudicatorPlayer> asPlayer() {
        return Optional.of(this);
    }

}
