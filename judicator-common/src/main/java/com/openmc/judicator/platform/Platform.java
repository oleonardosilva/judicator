package com.openmc.judicator.platform;

import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit);

    void dispatchConsoleCommand(String command);

    void announceToServerOf(JudicatorPlayer player, Component message);

}
