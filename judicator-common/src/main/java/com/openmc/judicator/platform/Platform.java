package com.openmc.judicator.platform;

import com.openmc.judicator.menus.MenuRenderer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface Platform {

    PlatformType getType();

    Logger getLogger();

    Path getDataDirectory();

    boolean isOnlineMode();

    Optional<JudicatorPlayer> getPlayer(String username);

    Optional<JudicatorPlayer> getPlayer(UUID uuid);

    Collection<? extends JudicatorPlayer> getOnlinePlayers();

    List<String> matchPlayerNames(String prefix);

    void runAsync(Runnable task);

    void runSync(Runnable task);

    /**
     * Runs the task every period off the game thread; the first run is one period from now.
     */
    PlatformTask runAsyncRepeating(Runnable task, long period, TimeUnit unit);

    void dispatchConsoleCommand(String command);

    /**
     * A class loader with the JDBC driver of the configured database, when the platform could not put it on the
     * plugin class path (Spigot/Paper before 1.16.5 has no {@code libraries:}); empty otherwise.
     */
    default Optional<ClassLoader> databaseDrivers() {
        return Optional.empty();
    }

    /**
     * What draws the menus of {@code /jmenu}: Spigot/Paper inventories; empty on the proxies (no {@code /jmenu}).
     */
    default Optional<MenuRenderer> menus() {
        return Optional.empty();
    }

    /**
     * The backend server the player is on (the proxy); empty on Spigot/Paper and while connecting.
     */
    Optional<String> serverOf(JudicatorPlayer player);

    /**
     * The backend servers the proxy knows; empty on Spigot/Paper.
     */
    List<String> serverNames();

    /**
     * The servers the proxy tries in order when a player joins or loses a server ({@code try} in velocity.toml);
     * empty on Spigot/Paper.
     */
    List<String> fallbackServers();

    /**
     * Sends the player to another backend server (the proxy).
     *
     * @return completes with whether the player got there; false at once on Spigot/Paper
     */
    CompletableFuture<Boolean> connect(JudicatorPlayer player, String server);

    /**
     * Takes a staff member to a player ({@code /reports tp}): on Spigot/Paper, a teleport; on the proxy, to the
     * player's server and then, when that backend runs Judicator in {@code proxy-mode}, to the player there
     * ({@link com.openmc.judicator.reports.TeleportRequest}).
     *
     * @return completes with how far the staff member got
     */
    CompletableFuture<TeleportOutcome> teleport(JudicatorPlayer staff, JudicatorPlayer target);

    default void announceToServerOf(JudicatorPlayer player, Component message) {
        announceToServerOf(player, recipient -> message);
    }

    /**
     * Sends a message to the players of the server the player is on, built for each recipient (in their language).
     */
    void announceToServerOf(JudicatorPlayer player, Function<JudicatorPlayer, Component> message);

}
