package com.elless.judicator.api.jail;

import java.util.Objects;

/**
 * A secret way out of a jail: a prisoner who gets within {@code radius} blocks of it escapes with a {@code chance} in
 * 100, and is caught otherwise.
 *
 * @param at     where it is
 * @param radius how close a prisoner must get, 0.5 to 10 blocks
 * @param chance the chance of escaping, 0 to 100
 */
public record JailEscape(JailPoint at, double radius, int chance) {

    public JailEscape {
        Objects.requireNonNull(at, "at");
        if (radius < 0.5 || radius > 10) throw new IllegalArgumentException("An escape radius is 0.5 to 10 blocks: " + radius);
        if (chance < 0 || chance > 100) throw new IllegalArgumentException("An escape chance is 0 to 100: " + chance);
    }

    boolean reaches(JailPoint point) {
        return at.world().equals(point.world()) && at.distanceSquared(point) <= radius * radius;
    }
}
