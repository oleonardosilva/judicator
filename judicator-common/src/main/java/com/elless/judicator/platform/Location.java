package com.elless.judicator.platform;

import java.util.Locale;
import java.util.Optional;

/**
 * A place in a world of the game server: the world as the platform names it ({@code world} on Spigot/Paper,
 * {@code minecraft:overworld} on the mod loaders), the coordinates and where the player looks.
 */
public record Location(String world, double x, double y, double z, float yaw, float pitch) {

    private static final String SEPARATOR = ";";

    public double distanceSquared(Location other) {
        final double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean sameWorld(Location other) {
        return world.equals(other.world);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /**
     * As it is stored: {@code world;x;y;z;yaw;pitch}.
     */
    public String encode() {
        return String.join(SEPARATOR, world, number(x), number(y), number(z), number(yaw), number(pitch));
    }

    /**
     * @return empty for null or anything {@link #encode} did not write
     */
    public static Optional<Location> decode(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        final String[] parts = text.split(SEPARATOR);
        if (parts.length != 6) return Optional.empty();
        try {
            return Optional.of(new Location(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether a world name can be stored: {@link #encode} separates with {@code ;}.
     */
    public static boolean storable(String world) {
        return world != null && !world.isEmpty() && !world.contains(SEPARATOR);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
