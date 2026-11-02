package com.elless.judicator.jails;

import com.elless.judicator.platform.Location;

import java.util.Optional;

/**
 * The blocks a prisoner is kept in: a box in one world, both corners included.
 */
public record JailArea(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /**
     * The box between two blocks, in any order.
     */
    public static JailArea between(Location a, Location b) {
        return new JailArea(a.world(),
                Math.min(a.blockX(), b.blockX()), Math.min(a.blockY(), b.blockY()), Math.min(a.blockZ(), b.blockZ()),
                Math.max(a.blockX(), b.blockX()), Math.max(a.blockY(), b.blockY()), Math.max(a.blockZ(), b.blockZ()));
    }

    /**
     * A cube of {@code radius} blocks in every direction around a place.
     */
    public static JailArea around(Location center, int radius) {
        return new JailArea(center.world(),
                center.blockX() - radius, center.blockY() - radius, center.blockZ() - radius,
                center.blockX() + radius, center.blockY() + radius, center.blockZ() + radius);
    }

    public boolean contains(Location location) {
        return world.equals(location.world())
                && location.x() >= minX && location.x() < maxX + 1
                && location.y() >= minY && location.y() < maxY + 1
                && location.z() >= minZ && location.z() < maxZ + 1;
    }

    /**
     * A box with this corner moved to the block: the corner nearest to it in each axis.
     */
    public JailArea withCorner(Location corner, boolean first) {
        final Location other = first
                ? new Location(world, maxX, maxY, maxZ, 0, 0)
                : new Location(world, minX, minY, minZ, 0, 0);
        return between(corner, other);
    }

    /**
     * As it is stored: {@code world;minX;minY;minZ;maxX;maxY;maxZ}.
     */
    public String encode() {
        return String.join(";", world, String.valueOf(minX), String.valueOf(minY), String.valueOf(minZ),
                String.valueOf(maxX), String.valueOf(maxY), String.valueOf(maxZ));
    }

    public static Optional<JailArea> decode(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        final String[] parts = text.split(";");
        if (parts.length != 7) return Optional.empty();
        try {
            return Optional.of(new JailArea(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * {@code x1 y1 z1 -> x2 y2 z2}, for the staff.
     */
    public String describe() {
        return minX + " " + minY + " " + minZ + " -> " + maxX + " " + maxY + " " + maxZ;
    }
}
