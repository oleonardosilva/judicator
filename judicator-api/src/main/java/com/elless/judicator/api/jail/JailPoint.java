package com.elless.judicator.api.jail;

import java.util.Objects;

/**
 * A place in a world of the jail's server: the world as the platform names it ({@code world} on Spigot/Paper,
 * {@code minecraft:overworld} on the mod loaders), the coordinates and where a player put there looks.
 */
public record JailPoint(String world, double x, double y, double z, float yaw, float pitch) {

    public JailPoint {
        Objects.requireNonNull(world, "world");
        if (world.isEmpty() || world.contains(";")) throw new IllegalArgumentException("A jail cannot use the world '" + world + "'");
    }

    /**
     * @return the point without a direction to look at
     */
    public static JailPoint of(String world, double x, double y, double z) {
        return new JailPoint(world, x, y, z, 0, 0);
    }

    double distanceSquared(JailPoint other) {
        final double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
