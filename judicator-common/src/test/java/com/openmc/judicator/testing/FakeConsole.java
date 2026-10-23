package com.openmc.judicator.testing;

import com.openmc.judicator.platform.JudicatorPlayer;
import com.openmc.judicator.platform.JudicatorSender;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The server console: every permission, no player.
 */
public final class FakeConsole implements JudicatorSender {

    private final List<Component> received = new ArrayList<>();

    @Override
    public String getName() {
        return "CONSOLE";
    }

    @Override
    public boolean hasPermission(String permission) {
        return true;
    }

    @Override
    public void sendMessage(Component message) {
        received.add(message);
    }

    @Override
    public Optional<JudicatorPlayer> asPlayer() {
        return Optional.empty();
    }

    public List<String> messages() {
        return received.stream().map(Texts::plain).toList();
    }

    /**
     * Commands behind the clickable parts of every message received.
     */
    public List<String> clicks() {
        return received.stream().flatMap(message -> Texts.clicks(message).stream()).toList();
    }

    public List<String> hovers() {
        return received.stream().flatMap(message -> Texts.hovers(message).stream()).toList();
    }

    /**
     * Commands put in the chat box by clicking parts of the messages received.
     */
    public List<String> suggestions() {
        return received.stream().flatMap(message -> Texts.suggestions(message).stream()).toList();
    }

    public boolean received(String fragment) {
        return messages().stream().anyMatch(message -> message.contains(fragment));
    }

    public void clearMessages() {
        received.clear();
    }
}
