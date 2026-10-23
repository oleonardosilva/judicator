package com.openmc.judicator.platform;

import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface Platform {

    PlatformType getType();

    Logger getLogger();

    Path getDataDirectory();

    boolean isOnlineMode();

    Optional<JudicatorPlayer> getPlayer(String username);

    Collection<? extends JudicatorPlayer> getOnlinePlayers();

    List<String> matchPlayerNames(String prefix);

    void runAsync(Runnable task);

    void runSync(Runnable task);

    /**
     * Runs the task every period off the game thread; the first run is one period from now.
     */
    PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit);

    void dispatchConsoleCommand(String command);

    default void announceToServerOf(JudicatorPlayer player, Component message) {
        announceToServerOf(player, recipient -> message);
    }

    /**
     * Sends a message to the players of the server the player is on, built for each recipient (in their language).
     */
    void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message);

}
