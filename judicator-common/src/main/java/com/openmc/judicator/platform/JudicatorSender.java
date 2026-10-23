package com.openmc.judicator.platform;

import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Optional;

public interface JudicatorSender {

    String getName();

    boolean hasPermission(String permission);

    /**
     * Like {@link #hasPermission(String)}, but ignores platform defaults such as Bukkit granting every undeclared
     * permission to operators. Used for nodes that must be given on purpose, like {@code judicator.level.<N>}.
     */
    default boolean hasExplicitPermission(String permission) {
        return hasPermission(permission);
    }

    void sendMessage(Component message);

    /**
     * The language of the game client (e.g. {@code pt_BR}); empty for the console, or while the client has not sent
     * its settings yet.
     */
    default Optional<Locale> getLocale() {
        return Optional.empty();
    }

    Optional<JudicatorPlayer> asPlayer();

    default boolean isPlayer() {
        return asPlayer().isPresent();
    }

}
