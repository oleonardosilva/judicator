package com.elless.judicator.jails;

import com.elless.judicator.platform.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A jail made with {@code /jails}: where prisoners are put ({@code spawn}) and kept ({@code area}), on which server of
 * the network, how many it holds, its secret ways out and where a prisoner goes when freed.
 *
 * @param server   the server it is on as the proxy calls it ({@code scopes.server-name} of the server it was made on);
 *                 empty on a server without a name, which only that server can use
 * @param release  where a freed prisoner goes; empty for the spawn of the server's first world
 * @param shared   false: one prisoner at a time
 * @param capacity the most prisoners of a shared jail; 0 for no limit
 */
public record Jail(String name, String server, Location spawn, JailArea area, Optional<Location> release,
                   boolean shared, int capacity, List<EscapeRoute> escapes) {

    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    /** blocks around the spawn a new jail keeps prisoners in, until {@code /jails pos1|pos2} */
    public static final int DEFAULT_RADIUS = 3;
    public static final int MAX_ESCAPES = 10;

    public Jail {
        escapes = List.copyOf(escapes);
        capacity = Math.max(0, capacity);
    }

    public static Jail create(String name, String server, Location spawn) {
        return new Jail(name, server, spawn, JailArea.around(spawn, DEFAULT_RADIUS), Optional.empty(), true, 0, List.of());
    }

    /**
     * How many prisoners it holds at most.
     */
    public int room() {
        if (!shared) return 1;
        return capacity == 0 ? Integer.MAX_VALUE : capacity;
    }

    public Optional<EscapeRoute> escapeAt(Location location) {
        return escapes.stream().filter(route -> route.reaches(location)).findFirst();
    }

    public Jail withServer(String server) {
        return new Jail(name, server, spawn, area, release, shared, capacity, escapes);
    }

    public Jail withSpawn(Location spawn) {
        return new Jail(name, server, spawn, area, release, shared, capacity, escapes);
    }

    public Jail withArea(JailArea area) {
        return new Jail(name, server, spawn, area, release, shared, capacity, escapes);
    }

    public Jail withRelease(Location release) {
        return new Jail(name, server, spawn, area, Optional.ofNullable(release), shared, capacity, escapes);
    }

    public Jail withShared(boolean shared) {
        return new Jail(name, server, spawn, area, release, shared, capacity, escapes);
    }

    public Jail withCapacity(int capacity) {
        return new Jail(name, server, spawn, area, release, shared, capacity, escapes);
    }

    public Jail withEscape(EscapeRoute route) {
        final List<EscapeRoute> routes = new ArrayList<>(escapes);
        routes.add(route);
        return new Jail(name, server, spawn, area, release, shared, capacity, routes);
    }

    /**
     * @param index from 1, as {@code /jails escapes} lists them
     */
    public Jail withoutEscape(int index) {
        final List<EscapeRoute> routes = new ArrayList<>(escapes);
        routes.remove(index - 1);
        return new Jail(name, server, spawn, area, release, shared, capacity, routes);
    }

    public Jail withoutEscapes() {
        return new Jail(name, server, spawn, area, release, shared, capacity, List.of());
    }
}
