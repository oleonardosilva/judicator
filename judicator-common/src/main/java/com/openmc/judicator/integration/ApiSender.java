package com.openmc.judicator.integration;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import net.kyori.adventure.text.Component;

import java.util.Optional;

/**
 * The plugin calling the API, seen by the staff rules as the console: no hierarchy, no time limits, no cooldown wait
 * (exemptions still apply). What the rules would tell it is dropped: the API answers with a status instead.
 */
final class ApiSender implements JudicatorSender {

    private final String name;

    ApiSender(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void sendMessage(Component message) {
    }

    @Override
    public Optional<JudicatorPlayer> asPlayer() {
        return Optional.empty();
    }

}
