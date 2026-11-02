package com.elless.judicator.api.jail;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Everything about a jail, to read it ({@link com.elless.judicator.api.JudicatorApi#getJail}) or to make or change it
 * ({@link com.elless.judicator.api.JudicatorApi#saveJail}), as {@code /jails} does in game.
 *
 * @param name     up to 32 of a-z, 0-9, _ and -
 * @param server   the server of the network it is on, as the proxy calls it ({@code scopes.server-name} there); empty
 *                 for a server that is not behind a proxy
 * @param spawn    where prisoners are put; inside the area, out of reach of every escape route
 * @param corner1  a corner of the area prisoners are kept in (whole blocks, both corners included)
 * @param corner2  the opposite corner, in the same world
 * @param release  where freed prisoners go; empty for the spawn of the server's first world
 * @param shared   false: one prisoner at a time
 * @param capacity the most prisoners of a shared jail; 0 for no limit
 * @param escapes  the secret ways out, at most {@value #MAX_ESCAPES}
 */
public record JailDefinition(String name, Optional<String> server, JailPoint spawn, JailPoint corner1, JailPoint corner2,
                             Optional<JailPoint> release, boolean shared, int capacity, List<JailEscape> escapes) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    /** most escape routes of a jail */
    public static final int MAX_ESCAPES = 10;

    /**
     * @throws IllegalArgumentException when the jail is not possible (the message says why)
     */
    public JailDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(corner1, "corner1");
        Objects.requireNonNull(corner2, "corner2");
        Objects.requireNonNull(release, "release");
        escapes = List.copyOf(escapes);
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("A jail name is up to 32 of a-z, 0-9, _ and -: " + name);
        if (server.isPresent() && !NAME.matcher(server.get()).matches()) {
            throw new IllegalArgumentException("A server name is up to 32 of a-z, 0-9, _ and -: " + server.get());
        }
        if (!corner1.world().equals(corner2.world()) || !corner1.world().equals(spawn.world())) {
            throw new IllegalArgumentException("The spawn and both corners must be in one world");
        }
        if (!inside(spawn, corner1, corner2)) throw new IllegalArgumentException("The spawn must be inside the area");
        if (capacity < 0) throw new IllegalArgumentException("The capacity cannot be negative");
        if (escapes.size() > MAX_ESCAPES) throw new IllegalArgumentException("A jail has at most " + MAX_ESCAPES + " escape routes");
        if (escapes.stream().anyMatch(escape -> escape.reaches(spawn))) {
            throw new IllegalArgumentException("An escape route cannot reach the spawn: prisoners are put back there");
        }
    }

    /**
     * @return whether the point is in the area
     */
    public boolean inside(JailPoint point) {
        return inside(point, corner1, corner2);
    }

    private static boolean inside(JailPoint point, JailPoint corner1, JailPoint corner2) {
        return point.world().equals(corner1.world())
                && within(point.x(), corner1.x(), corner2.x())
                && within(point.y(), corner1.y(), corner2.y())
                && within(point.z(), corner1.z(), corner2.z());
    }

    private static boolean within(double value, double a, double b) {
        return value >= Math.floor(Math.min(a, b)) && value < Math.floor(Math.max(a, b)) + 1;
    }
}
