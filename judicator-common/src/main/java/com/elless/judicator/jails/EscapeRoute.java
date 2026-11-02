package com.elless.judicator.jails;

import com.elless.judicator.platform.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A secret way out of a jail: a prisoner who reaches it (within {@code radius} blocks) escapes with a {@code chance}
 * in 100, and is caught otherwise. Nothing shows where it is.
 */
public record EscapeRoute(Location at, double radius, int chance) {

    public static final double DEFAULT_RADIUS = 1.5;
    public static final double MAX_RADIUS = 10;

    public EscapeRoute {
        radius = Math.max(0.5, Math.min(MAX_RADIUS, radius));
        chance = Math.max(0, Math.min(100, chance));
    }

    public boolean reaches(Location location) {
        return at.sameWorld(location) && at.distanceSquared(location) <= radius * radius;
    }

    /**
     * The routes as they are stored: {@code world;x;y;z;radius;chance} joined by {@code |}.
     */
    public static String encode(List<EscapeRoute> routes) {
        final List<String> parts = new ArrayList<>();
        for (EscapeRoute route : routes) {
            final Location at = route.at();
            parts.add(new Location(at.world(), at.x(), at.y(), at.z(), (float) route.radius(), route.chance()).encode());
        }
        return String.join("|", parts);
    }

    /**
     * @return the routes {@link #encode} wrote; anything else is skipped
     */
    public static List<EscapeRoute> decode(String text) {
        final List<EscapeRoute> routes = new ArrayList<>();
        if (text == null || text.isBlank()) return routes;
        for (String part : text.split("\\|")) {
            Location.decode(part).ifPresent(stored -> routes.add(new EscapeRoute(
                    new Location(stored.world(), stored.x(), stored.y(), stored.z(), 0, 0), stored.yaw(), Math.round(stored.pitch()))));
        }
        return routes;
    }

    /**
     * {@code x y z}, for the staff.
     */
    public String describe() {
        return String.format(Locale.ROOT, "%d %d %d", at.blockX(), at.blockY(), at.blockZ());
    }
}
